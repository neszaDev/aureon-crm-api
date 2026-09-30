package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static AuthProperties props(String secret) {
    return new AuthProperties(
        new AuthProperties.Jwt(secret, Duration.ofMinutes(15)), Duration.ofDays(30));
  }

  @Test
  void rejectsShortSecretWithoutLeakingIt() {
    assertThatThrownBy(() -> new JwtService(props("my-real-but-short-secret")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.jwt.secret")
        .hasMessageNotContaining("my-real-but-short-secret");
  }
}
