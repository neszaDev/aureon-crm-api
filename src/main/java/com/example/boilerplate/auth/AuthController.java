package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;
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
  AuthTokensResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
    return service.register(r, req.getRemoteAddr());
  }

  @PostMapping("/login")
  AuthTokensResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
    // The client's IP: Tomcat resolves X-Forwarded-For from trusted proxies (application.yml).
    return service.login(r, req.getRemoteAddr());
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
