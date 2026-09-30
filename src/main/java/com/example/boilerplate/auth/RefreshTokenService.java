package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.user.User;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
  private final RefreshTokenRepository tokens;
  private final JwtService jwt;
  private final Duration ttl;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository tokens,
      JwtService jwt,
      @Value("${app.security.refresh-token-ttl}") Duration ttl) {
    this.tokens = tokens;
    this.jwt = jwt;
    this.ttl = ttl;
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
