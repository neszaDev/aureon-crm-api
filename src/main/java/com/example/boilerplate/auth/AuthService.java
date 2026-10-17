package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.*;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
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
    if (!registrationEnabled) throw new AccessDeniedException("Registration is disabled");
    requireHashable("password", r.password());
    rateLimiter.consume(Policy.REGISTER_PER_IP, clientIp);
    if (users.existsByEmail(r.email().toLowerCase()))
      throw new ConflictException("Email is already registered");
    return refreshTokens.create(users.save(new User(r.email(), encoder.encode(r.password()))));
  }

  /**
   * Limits attempts per client IP, and failed attempts per email: guessing one account's password
   * is slow from any number of IPs. A blocked email stays blocked for the right password too.
   */
  public AuthTokensResponse login(LoginRequest r, String clientIp) {
    String email = r.email().toLowerCase();
    rateLimiter.consume(Policy.LOGIN_PER_IP, clientIp);
    rateLimiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, email);
    User u =
        users
            .findByEmail(email)
            .filter(found -> encoder.matches(r.password(), found.getPasswordHash()))
            .orElse(null);
    if (u == null) {
      rateLimiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, email);
      throw new BadCredentialsException("Invalid email or password");
    }
    return refreshTokens.create(u);
  }

  /**
   * A wrong current password counts toward the same per-email failure limit as a wrong sign-in
   * password, and is a 400 on the field, never a 401: a 401 tells the client the session is gone.
   * Every session is revoked and a new one returned.
   */
  public AuthTokensResponse changePassword(String email, ChangePasswordRequest r) {
    requireHashable("newPassword", r.newPassword());
    rateLimiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, email);
    User u =
        users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
      rateLimiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, email);
      throw new FieldValidationException("currentPassword", "is incorrect");
    }
    u.changePasswordHash(encoder.encode(r.newPassword()));
    refreshTokens.revokeAll(u.getId());
    return refreshTokens.create(u);
  }

  public AuthTokensResponse refresh(String refreshToken) {
    return refreshTokens.rotate(refreshToken);
  }

  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }

  /** {@code @Size(max = 72)} counts characters, but BCrypt throws above 72 bytes (e.g. Thai). */
  private static void requireHashable(String field, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new FieldValidationException(field, "must be at most 72 bytes");
  }
}
