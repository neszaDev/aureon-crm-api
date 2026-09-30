package com.example.boilerplate.user;

import com.example.boilerplate.user.dto.UserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping("/me")
  UserResponse me(Authentication authentication) {
    return service.currentUser(authentication.getName());
  }
}
