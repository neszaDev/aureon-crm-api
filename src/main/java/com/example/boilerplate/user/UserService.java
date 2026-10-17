package com.example.boilerplate.user;

import com.example.boilerplate.user.dto.UpdateProfileRequest;
import com.example.boilerplate.user.dto.UserResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;
  private final UserMapper mapper;

  public UserService(UserRepository users, UserMapper mapper) {
    this.users = users;
    this.mapper = mapper;
  }

  public UserResponse currentUser(String email) {
    return mapper.toResponse(find(email));
  }

  /** Trims the name; a blank one clears it. */
  @Transactional
  public UserResponse updateProfile(String email, UpdateProfileRequest r) {
    User user = find(email);
    String name = r.displayName() == null ? "" : r.displayName().strip();
    user.changeDisplayName(name.isEmpty() ? null : name);
    return mapper.toResponse(user);
  }

  private User find(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }
}
