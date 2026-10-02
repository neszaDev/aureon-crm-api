package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserAccessChangedEvent;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
  private final RefreshTokenRepository tokens;
  private final JwtService jwt;
  private final Duration ttl;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository tokens, JwtService jwt, AuthProperties properties) {
    this.tokens = tokens;
    this.jwt = jwt;
    this.ttl = properties.refreshTokenTtl();
  }

  public AuthTokensResponse create(User user) {
    tokens.deleteByExpiresAtBefore(Instant.now());
    String raw = generate();
    tokens.save(new RefreshToken(user, hash(raw), Instant.now().plus(ttl)));
    return response(user, raw);
  }

  public AuthTokensResponse rotate(String raw) {
    RefreshToken token =
        tokens.findByTokenHashForUpdate(hash(raw)).orElseThrow(InvalidRefreshTokenException::new);
    if (token.getExpiresAt().isBefore(Instant.now())) {
      tokens.delete(token);
      throw new InvalidRefreshTokenException();
    }
    User user = token.getUser();
    tokens.delete(token);
    return create(user);
  }

  public void revoke(String raw) {
    tokens.findByTokenHashForUpdate(hash(raw)).ifPresent(tokens::delete);
  }

  /** A changed email or role signs the user out everywhere (runs in the caller's transaction). */
  @EventListener
  void revokeAll(UserAccessChangedEvent event) {
    tokens.deleteByUserId(event.userId());
  }

  private AuthTokensResponse response(User user, String refresh) {
    return new AuthTokensResponse(
        jwt.issue(user), "Bearer", jwt.expiresInSeconds(), refresh, ttl.toSeconds());
  }

  private String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
