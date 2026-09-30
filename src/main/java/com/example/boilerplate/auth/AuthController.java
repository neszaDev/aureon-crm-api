package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthTokensResponse register(@Valid @RequestBody RegisterRequest r) {
    return service.register(r);
  }

  @PostMapping("/login")
  AuthTokensResponse login(@Valid @RequestBody LoginRequest r) {
    return service.login(r);
  }

  @PostMapping("/refresh")
  AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest r) {
    return service.refresh(r.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@Valid @RequestBody RefreshTokenRequest r) {
    service.logout(r.refreshToken());
  }
}
