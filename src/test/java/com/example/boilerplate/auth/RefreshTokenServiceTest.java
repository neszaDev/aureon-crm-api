package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
  @Mock RefreshTokenRepository tokens;
  @Mock JwtService jwt;
  RefreshTokenService service;
  final User user = new User("me@example.com", "hash");

  @BeforeEach
  void setUp() {
    service =
        new RefreshTokenService(
            tokens,
            jwt,
            new AuthProperties(
                new AuthProperties.Jwt("x".repeat(32), Duration.ofMinutes(15)),
                Duration.ofDays(30)));
  }

  @Test
  void createStoresOnlyTheHashOfTheIssuedToken() throws Exception {
    var response = service.create(user);

    var saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens).save(saved.capture());
    verify(tokens).deleteByExpiresAtBefore(any());
    assertThat(response.refreshToken()).isNotBlank();
    assertThat(response.refreshExpiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
    assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    // Only the SHA-256 hex digest is persisted, never the raw token.
    assertThat(saved.getValue())
        .extracting("tokenHash")
        .isEqualTo(sha256Hex(response.refreshToken()));
  }

  private static String sha256Hex(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void rotateDeletesOldTokenAndIssuesNewOne() {
    var existing = new RefreshToken(user, "h", Instant.now().plusSeconds(60));
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(existing));

    var response = service.rotate("raw");

    verify(tokens).delete(existing);
    verify(tokens).save(any(RefreshToken.class));
    assertThat(response.refreshToken()).isNotEqualTo("raw");
  }

  @Test
  void rotateRejectsAndDeletesExpiredToken() {
    var expired = new RefreshToken(user, "h", Instant.now().minusSeconds(1));
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate("raw"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(tokens).delete(expired);
    verify(tokens, never()).save(any());
  }

  @Test
  void rotateRejectsUnknownToken() {
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.rotate("raw"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void revokeOfUnknownTokenIsANoOp() {
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
    service.revoke("raw");
    verify(tokens, never()).delete(any());
  }
}
