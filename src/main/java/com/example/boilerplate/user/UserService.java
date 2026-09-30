package com.example.boilerplate.user;

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
    return users
        .findByEmail(email)
        .map(mapper::toResponse)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }
}
