package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import com.example.boilerplate.common.exception.ConflictException;
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

  public AuthService(
      UserRepository users, PasswordEncoder encoder, RefreshTokenService refreshTokens) {
    this.users = users;
    this.encoder = encoder;
    this.refreshTokens = refreshTokens;
  }

  public AuthTokensResponse register(RegisterRequest r) {
    if (users.existsByEmail(r.email().toLowerCase()))
      throw new ConflictException("Email is already registered");
    return refreshTokens.create(users.save(new User(r.email(), encoder.encode(r.password()))));
  }

  public AuthTokensResponse login(LoginRequest r) {
    User u =
        users
            .findByEmail(r.email().toLowerCase())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    if (!encoder.matches(r.password(), u.getPasswordHash()))
      throw new BadCredentialsException("Invalid email or password");
    return refreshTokens.create(u);
  }

  public AuthTokensResponse refresh(String refreshToken) {
    return refreshTokens.rotate(refreshToken);
  }

  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }
}
