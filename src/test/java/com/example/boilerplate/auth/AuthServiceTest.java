package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

import com.example.boilerplate.auth.dto.LoginRequest;
import com.example.boilerplate.auth.dto.RegisterRequest;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.RateLimitExceededException;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
    service = serviceWithRegistration(true);
  }

  private AuthService serviceWithRegistration(boolean registrationEnabled) {
    return new AuthService(
        users,
        encoder,
        refreshTokens,
        rateLimiter,
        new AuthProperties(
            new AuthProperties.Jwt("x".repeat(32), Duration.ofMinutes(15)),
            Duration.ofDays(30),
            registrationEnabled));
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
    verify(encoder).matches("wrong", "h");
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
    // As slow as a wrong password: the password is checked against a dummy BCrypt hash.
    verify(encoder).matches(eq("pw"), startsWith("$2"));
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

  @Test
  void registerIsRefusedWhenRegistrationDisabled() {
    var closed = serviceWithRegistration(false);

    var e =
        assertThrows(
            AccessDeniedException.class,
            () ->
                closed.register(
                    new RegisterRequest("b@example.com", "a-long-enough-password"), IP));
    assertEquals("Registration is disabled", e.getMessage());
    verify(users, never()).save(any());
  }

  @Test
  void changePasswordStoresNewHashRevokesSessionsAndStartsANewOne() {
    User user = user7();
    when(encoder.matches("current", "old-hash")).thenReturn(true);
    when(encoder.encode("brand-new-password")).thenReturn("new-hash");

    service.changePassword(7L, new ChangePasswordRequest("current", "brand-new-password"));

    assertEquals("new-hash", user.getPasswordHash());
    verify(refreshTokens).revokeAll(7L);
    verify(refreshTokens).create(user);
    verify(rateLimiter, never()).record(any(), any());
  }

  @Test
  void changePasswordWithWrongCurrentPasswordIsAFieldErrorAndCountsAsAFailure() {
    User user = user7();
    when(encoder.matches("wrong", "old-hash")).thenReturn(false);

    var e =
        assertThrows(
            FieldValidationException.class,
            () ->
                service.changePassword(
                    7L, new ChangePasswordRequest("wrong", "brand-new-password")));
    assertEquals("currentPassword", e.field());
    assertEquals("old-hash", user.getPasswordHash());
    verify(rateLimiter).record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    verifyNoInteractions(refreshTokens);
  }

  @Test
  void passwordsOver72BytesAreFieldErrorsNotServerErrors() {
    String thai = "ก".repeat(30); // 30 characters, 90 bytes: BCrypt would throw
    var register =
        assertThrows(
            FieldValidationException.class,
            () -> service.register(new RegisterRequest("b@example.com", thai), IP));
    assertEquals("password", register.field());
    verifyNoInteractions(users, encoder);

    var change =
        assertThrows(
            FieldValidationException.class,
            () -> service.changePassword(7L, new ChangePasswordRequest("old", thai)));
    assertEquals("newPassword", change.field());
    verifyNoInteractions(users, encoder, refreshTokens);
  }

  @Test
  void changePasswordIsRefusedWhileTheEmailIsBlocked() {
    user7();
    doThrow(new RateLimitExceededException(Duration.ofSeconds(30)))
        .when(rateLimiter)
        .check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");

    assertThrows(
        RateLimitExceededException.class,
        () -> service.changePassword(7L, new ChangePasswordRequest("right", "brand-new-password")));
    verifyNoInteractions(encoder, refreshTokens);
  }

  /** User 7, a@example.com with "old-hash", found by its id. */
  private User user7() {
    User user = new User("a@example.com", "old-hash");
    ReflectionTestUtils.setField(user, "id", 7L);
    when(users.findById(7L)).thenReturn(Optional.of(user));
    return user;
  }
}
