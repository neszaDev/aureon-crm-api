package com.example.boilerplate.user;

import com.example.boilerplate.common.security.CurrentUser;
import com.example.boilerplate.user.dto.UpdateProfileRequest;
import com.example.boilerplate.user.dto.UserResponse;
import jakarta.validation.Valid;
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
    return service.currentUser(CurrentUser.id(authentication));
  }

  @PutMapping("/me")
  UserResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest r) {
    return service.updateProfile(CurrentUser.id(authentication), r);
  }
}
