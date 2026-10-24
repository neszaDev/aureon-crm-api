package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.common.security.AccountEvents;
import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserAccessChangedEvent;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens in families: a sign-in starts one, each rotation adds a token to it and keeps the
 * old one as used. Expired rows, used or not, are deleted on every sign-in and rotation.
 */
@Service
public class RefreshTokenService {
  private final RefreshTokenRepository tokens;
  private final JwtService jwt;
  private final Duration ttl;
  private final Duration reuseGrace;
  private final Duration sessionMaxAge;
  private final RateLimiter rateLimiter;

  public RefreshTokenService(
      RefreshTokenRepository tokens,
      JwtService jwt,
      AuthProperties properties,
      SessionProperties sessions,
      RateLimiter rateLimiter) {
    this.tokens = tokens;
    this.jwt = jwt;
    this.rateLimiter = rateLimiter;
    this.ttl = properties.refreshTokenTtl();
    this.reuseGrace = sessions.refreshReuseGrace();
    this.sessionMaxAge = sessions.sessionMaxAge();
  }

  /** A sign-in: a new family. */
  public AuthTokensResponse create(User user) {
    Instant now = Instant.now();
    return issue(user, UUID.randomUUID(), now, now);
  }

  /**
   * A used token presented again is a replay, or a parallel refresh: within the grace only this
   * request fails; later the whole family goes, so a thief's newer token dies with it. A token of
   * an older token version (an email, role or password change since) takes its family along too.
   * The caller must commit the revocation although this throws (AuthService.refresh:
   * noRollbackFor). Only rotations that succeed are limited per user (a 429 changes nothing): each
   * keeps a row.
   */
  public AuthTokensResponse rotate(String raw) {
    RefreshToken token =
        tokens
            .findByTokenHashForUpdate(Tokens.sha256Hex(raw))
            .orElseThrow(InvalidRefreshTokenException::new);
    Instant now = Instant.now();
    if (token.getUsedAt() != null) {
      if (now.isAfter(token.getUsedAt().plus(reuseGrace))) {
        tokens.deleteByFamilyId(token.getFamilyId());
        AccountEvents.record("refresh.reuse", null, token.getUser().getId(), List.of());
      }
      throw new InvalidRefreshTokenException();
    }
    if (token.getExpiresAt().isBefore(now)
        || token.getTokenVersion() != token.getUser().getTokenVersion()
        || token.getFamilyCreatedAt().plus(sessionMaxAge).isBefore(now)) {
      tokens.deleteByFamilyId(token.getFamilyId());
      throw new InvalidRefreshTokenException();
    }
    // Only a rotation that will succeed counts: a replay or a losing parallel call must not spend
    // the budget, or a thief could drain it and turn the victim's replay into a 429.
    rateLimiter.consume(Policy.REFRESH_PER_USER, String.valueOf(token.getUser().getId()));
    token.markUsed(now);
    return issue(token.getUser(), token.getFamilyId(), token.getFamilyCreatedAt(), now);
  }

  /** Logout: ends the token's session, its whole family. */
  public void revoke(String raw) {
    tokens
        .findByTokenHashForUpdate(Tokens.sha256Hex(raw))
        .ifPresent(token -> tokens.deleteByFamilyId(token.getFamilyId()));
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

  private AuthTokensResponse issue(User user, UUID family, Instant familyCreatedAt, Instant now) {
    tokens.deleteByExpiresAtBefore(now);
    String raw = Tokens.random();
    tokens.save(
        new RefreshToken(user, Tokens.sha256Hex(raw), now.plus(ttl), family, familyCreatedAt));
    return new AuthTokensResponse(
        jwt.issue(user), "Bearer", jwt.expiresInSeconds(), raw, ttl.toSeconds());
  }
}
