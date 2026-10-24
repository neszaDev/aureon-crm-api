package com.example.boilerplate.user;

import com.example.boilerplate.common.text.Texts;
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

  public UserResponse currentUser(long userId) {
    return mapper.toResponse(find(userId));
  }

  /** Strips the name; a blank one clears it. */
  @Transactional
  public UserResponse updateProfile(long userId, UpdateProfileRequest r) {
    User user = find(userId);
    user.changeDisplayName(Texts.optional(r.displayName()));
    return mapper.toResponse(user);
  }

  private User find(long userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }
}
