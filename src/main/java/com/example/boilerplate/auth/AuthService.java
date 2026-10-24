package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.ForbiddenException;
import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.common.security.AccountEvents;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.*;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
  /**
   * An unknown email still costs a BCrypt check, so the answer's timing doesn't tell which emails
   * have accounts. Cost 10, as SecurityConfig's encoder; change both together.
   */
  static final String DUMMY_HASH = "$2y$10$ziAMVF0Pz7opLX1fQannfO.ax.qOE90VJcIKWHoYK0ruYNO7LKXb6";

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final RefreshTokenService refreshTokens;
  private final RateLimiter rateLimiter;
  private final boolean registrationEnabled;

  public AuthService(
      UserRepository users,
      PasswordEncoder encoder,
      RefreshTokenService refreshTokens,
      RateLimiter rateLimiter,
      AuthProperties properties) {
    this.users = users;
    this.encoder = encoder;
    this.refreshTokens = refreshTokens;
    this.rateLimiter = rateLimiter;
    this.registrationEnabled = properties.registrationEnabled();
  }

  public AuthTokensResponse register(RegisterRequest r, String clientIp) {
    if (!registrationEnabled)
      throw new ForbiddenException(ErrorCode.REGISTRATION_DISABLED, "Registration is disabled");
    requireHashable("password", r.password());
    rateLimiter.consume(Policy.REGISTER_PER_IP, clientIp);
    if (users.existsByEmail(r.email().toLowerCase(Locale.ROOT)))
      throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
    return refreshTokens.create(users.save(new User(r.email(), encoder.encode(r.password()))));
  }

  /**
   * Limits attempts per client IP, and failed attempts per email: guessing one account's password
   * is slow from any number of IPs. A blocked email stays blocked for the right password too.
   */
  public AuthTokensResponse login(LoginRequest r, String clientIp) {
    String email = r.email().toLowerCase(Locale.ROOT);
    rateLimiter.consume(Policy.LOGIN_PER_IP, clientIp);
    rateLimiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, email);
    User u = users.findByEmail(email).orElse(null);
    boolean matches = encoder.matches(r.password(), u == null ? DUMMY_HASH : u.getPasswordHash());
    if (u == null || !matches) {
      failed(email, u == null ? null : u.getId(), null);
      throw new BadCredentialsException("Invalid email or password");
    }
    return refreshTokens.create(u);
  }

  /**
   * A wrong current password counts toward the same per-email failure limit as a wrong sign-in
   * password, and is a 400 on the field, never a 401: a 401 tells the client the session is gone.
   * Every session is revoked and a new one returned.
   */
  public AuthTokensResponse changePassword(long userId, ChangePasswordRequest r) {
    requireHashable("newPassword", r.newPassword());
    User u =
        users.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    String email = u.getEmail();
    rateLimiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, email);
    if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
      failed(email, u.getId(), u.getId());
      throw new FieldValidationException("currentPassword", "is incorrect");
    }
    u.changePasswordHash(encoder.encode(r.newPassword()));
    refreshTokens.revokeAll(u.getId());
    AccountEvents.record("password.change", u.getId(), u.getId(), List.of("password"));
    return refreshTokens.create(u);
  }

  /** Counts a wrong password; the one that blocks the email is a sign-in lockout. */
  private void failed(String email, Long userId, Long actorId) {
    if (rateLimiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, email))
      AccountEvents.recordNow("sign-in.lockout", actorId, userId, List.of());
  }

  /** A replay revokes the token's family and answers 401: the revocation must commit. */
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public AuthTokensResponse refresh(String refreshToken) {
    return refreshTokens.rotate(refreshToken);
  }

  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }

  /** {@code @Size(max = 72)} counts characters, but BCrypt throws above 72 bytes (e.g. Thai). */
  public static void requireHashable(String field, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new FieldValidationException(field, "must be at most 72 bytes");
  }
}
