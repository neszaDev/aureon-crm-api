package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserAccessChangedEvent;
import java.time.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
  private final RefreshTokenRepository tokens;
  private final JwtService jwt;
  private final Duration ttl;

  public RefreshTokenService(
      RefreshTokenRepository tokens, JwtService jwt, AuthProperties properties) {
    this.tokens = tokens;
    this.jwt = jwt;
    this.ttl = properties.refreshTokenTtl();
  }

  public AuthTokensResponse create(User user) {
    tokens.deleteByExpiresAtBefore(Instant.now());
    String raw = Tokens.random();
    tokens.save(new RefreshToken(user, Tokens.sha256Hex(raw), Instant.now().plus(ttl)));
    return response(user, raw);
  }

  public AuthTokensResponse rotate(String raw) {
    RefreshToken token =
        tokens
            .findByTokenHashForUpdate(Tokens.sha256Hex(raw))
            .orElseThrow(InvalidRefreshTokenException::new);
    if (token.getExpiresAt().isBefore(Instant.now())) {
      tokens.delete(token);
      throw new InvalidRefreshTokenException();
    }
    User user = token.getUser();
    tokens.delete(token);
    return create(user);
  }

  public void revoke(String raw) {
    tokens.findByTokenHashForUpdate(Tokens.sha256Hex(raw)).ifPresent(tokens::delete);
  }

  /** Signs the user out everywhere (runs in the caller's transaction). */
  public void revokeAll(long userId) {
    tokens.deleteByUserId(userId);
  }

  /** A changed email or role signs the user out everywhere. */
  @EventListener
  void onUserAccessChanged(UserAccessChangedEvent event) {
    revokeAll(event.userId());
  }

  private AuthTokensResponse response(User user, String refresh) {
    return new AuthTokensResponse(
        jwt.issue(user), "Bearer", jwt.expiresInSeconds(), refresh, ttl.toSeconds());
  }
}
