package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.boilerplate.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final JwtService jwt = new JwtService(props(SECRET), new MockEnvironment());

  private static AuthProperties props(String secret) {
    return new AuthProperties(
        new AuthProperties.Jwt(secret, Duration.ofMinutes(15)), Duration.ofDays(30), true);
  }

  @Test
  void issuedTokenParsesBackToSubjectUserIdAndRole() {
    User user = new User("Me@Example.com", "hash");
    ReflectionTestUtils.setField(user, "id", 42L);
    var claims = jwt.parse(jwt.issue(user));
    assertThat(claims.getSubject()).isEqualTo("me@example.com");
    assertThat(claims.get(JwtService.USER_ID, Long.class)).isEqualTo(42L);
    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
    assertThat(jwt.expiresInSeconds()).isEqualTo(900);
  }

  @Test
  void rejectsTokenSignedWithAnotherKey() {
    String foreign =
        new JwtService(props("another-secret-another-secret-00"), new MockEnvironment())
            .issue(new User("me@example.com", "hash"));
    assertThatThrownBy(() -> jwt.parse(foreign)).isInstanceOf(SignatureException.class);
  }

  @Test
  void rejectsExpiredToken() {
    Instant past = Instant.now().minus(Duration.ofHours(1));
    String expired =
        Jwts.builder()
            .subject("me@example.com")
            .issuedAt(Date.from(past))
            .expiration(Date.from(past.plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThatThrownBy(() -> jwt.parse(expired)).isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void rejectsShortSecretWithoutLeakingIt() {
    assertThatThrownBy(
            () -> new JwtService(props("my-real-but-short-secret"), new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.jwt.secret")
        .hasMessageNotContaining("my-real-but-short-secret");
  }
}
