package com.example.boilerplate.auth;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Lives in {@code auth/} so that {@code user/} never depends on it; the path sits under /users. */
@RestController
@RequestMapping("/api/v1/users/me/password")
public class PasswordController {
  private final AuthService service;

  public PasswordController(AuthService service) {
    this.service = service;
  }

  @PostMapping
  AuthTokensResponse change(
      Authentication authentication, @Valid @RequestBody ChangePasswordRequest r) {
    return service.changePassword(authentication.getName(), r);
  }
}
