package com.example.boilerplate.user;

import com.example.boilerplate.user.dto.UpdateUserRequest;
import com.example.boilerplate.user.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Admin-only user management; access is restricted in {@code SecurityConfig}. */
@Validated
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {
  private final UserAdminService service;

  public UserAdminController(UserAdminService service) {
    this.service = service;
  }

  @GetMapping
  Page<UserResponse> list(
      Authentication a,
      @RequestParam(required = false) @Size(max = 254) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(a.getName(), q, page, size);
  }

  @GetMapping("/{id}")
  UserResponse get(Authentication a, @PathVariable Long id) {
    return service.get(a.getName(), id);
  }

  @PatchMapping("/{id}")
  UserResponse update(
      Authentication a, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
    return service.update(a.getName(), id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(Authentication a, @PathVariable Long id) {
    service.delete(a.getName(), id);
  }
}
