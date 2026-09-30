package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.boilerplate.auth.dto.RegisterRequest;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;
  @Mock RefreshTokenService refreshTokens;

  @Test
  void rejectsDuplicateEmail() {
    when(users.existsByEmail("a@example.com")).thenReturn(true);
    var service = new AuthService(users, encoder, refreshTokens);
    assertThrows(
        ConflictException.class,
        () -> service.register(new RegisterRequest("a@example.com", "a-long-enough-password")));
    verifyNoMoreInteractions(encoder);
  }
}
