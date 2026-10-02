package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.boilerplate.auth.dto.LoginRequest;
import com.example.boilerplate.auth.dto.RegisterRequest;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.RateLimitExceededException;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest {
  static final String IP = "203.0.113.7";

  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;
  @Mock RefreshTokenService refreshTokens;
  @Mock RateLimiter rateLimiter;
  AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, encoder, refreshTokens, rateLimiter);
  }

  @Test
  void rejectsDuplicateEmail() {
    when(users.existsByEmail("a@example.com")).thenReturn(true);
    assertThrows(
        ConflictException.class,
        () -> service.register(new RegisterRequest("A@example.com", "a-long-enough-password"), IP));
    verifyNoMoreInteractions(encoder);
  }

  @Test
  void registerStoresEncodedPasswordAndLowerCaseEmail() {
    when(encoder.encode("a-long-enough-password")).thenReturn("encoded");
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    service.register(new RegisterRequest("New@Example.com", "a-long-enough-password"), IP);

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
        () -> service.login(new LoginRequest("a@example.com", "wrong"), IP));
    verifyNoInteractions(refreshTokens);
    verify(rateLimiter).record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
  }

  @Test
  void loginWithUnknownEmailFails() {
    when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("Nobody@example.com", "pw"), IP));
    verify(rateLimiter).record(Policy.LOGIN_FAILURES_PER_EMAIL, "nobody@example.com");
  }

  @Test
  void blockedEmailIsRejectedBeforeThePasswordIsChecked() {
    doThrow(new RateLimitExceededException(Duration.ofSeconds(30)))
        .when(rateLimiter)
        .check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");

    assertThrows(
        RateLimitExceededException.class,
        () -> service.login(new LoginRequest("A@example.com", "right"), IP));
    verify(rateLimiter).consume(Policy.LOGIN_PER_IP, IP);
    verifyNoInteractions(users, encoder, refreshTokens);
  }

  @Test
  void successfulLoginDoesNotCountAsAFailure() {
    User user = new User("a@example.com", "h");
    when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
    when(encoder.matches("right", "h")).thenReturn(true);

    service.login(new LoginRequest("a@example.com", "right"), IP);

    verify(refreshTokens).create(user);
    verify(rateLimiter, never()).record(any(), any());
  }

  @Test
  void registrationIsLimitedPerIp() {
    doThrow(new RateLimitExceededException(Duration.ofMinutes(1)))
        .when(rateLimiter)
        .consume(Policy.REGISTER_PER_IP, IP);

    assertThrows(
        RateLimitExceededException.class,
        () -> service.register(new RegisterRequest("b@example.com", "a-long-enough-password"), IP));
    verifyNoInteractions(users);
  }
}
