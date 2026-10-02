package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.*;
import org.springframework.security.authentication.BadCredentialsException;
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

  public AuthService(
      UserRepository users,
      PasswordEncoder encoder,
      RefreshTokenService refreshTokens,
      RateLimiter rateLimiter) {
    this.users = users;
    this.encoder = encoder;
    this.refreshTokens = refreshTokens;
    this.rateLimiter = rateLimiter;
  }

  public AuthTokensResponse register(RegisterRequest r, String clientIp) {
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

  public AuthTokensResponse refresh(String refreshToken) {
    return refreshTokens.rotate(refreshToken);
  }

  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }
}
