package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.boilerplate.auth.dto.LoginRequest;
import com.example.boilerplate.auth.dto.RegisterRequest;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;
  @Mock RefreshTokenService refreshTokens;
  AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, encoder, refreshTokens);
  }

  @Test
  void rejectsDuplicateEmail() {
    when(users.existsByEmail("a@example.com")).thenReturn(true);
    assertThrows(
        ConflictException.class,
        () -> service.register(new RegisterRequest("A@example.com", "a-long-enough-password")));
    verifyNoMoreInteractions(encoder);
  }

  @Test
  void registerStoresEncodedPasswordAndLowerCaseEmail() {
    when(encoder.encode("a-long-enough-password")).thenReturn("encoded");
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    service.register(new RegisterRequest("New@Example.com", "a-long-enough-password"));

    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertEquals("new@example.com", saved.getValue().getEmail());
    assertEquals("encoded", saved.getValue().getPasswordHash());
  }

  @Test
  void loginWithWrongPasswordFails() {
    when(users.findByEmail("a@example.com"))
        .thenReturn(Optional.of(new User("a@example.com", "h")));
    when(encoder.matches("wrong", "h")).thenReturn(false);
    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("a@example.com", "wrong")));
    verifyNoInteractions(refreshTokens);
  }

  @Test
  void loginWithUnknownEmailFails() {
    when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("Nobody@example.com", "pw")));
  }
}
