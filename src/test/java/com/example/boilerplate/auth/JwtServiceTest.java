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

class JwtServiceTest {
  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final JwtService jwt = new JwtService(props(SECRET));

  private static AuthProperties props(String secret) {
    return new AuthProperties(
        new AuthProperties.Jwt(secret, Duration.ofMinutes(15)), Duration.ofDays(30));
  }

  @Test
  void issuedTokenParsesBackToSubjectAndRole() {
    var claims = jwt.parse(jwt.issue(new User("Me@Example.com", "hash")));
    assertThat(claims.getSubject()).isEqualTo("me@example.com");
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
    assertThat(jwt.expiresInSeconds()).isEqualTo(900);
  }

  @Test
  void rejectsTokenSignedWithAnotherKey() {
    String foreign =
        new JwtService(props("another-secret-another-secret-00"))
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
    assertThatThrownBy(() -> new JwtService(props("my-real-but-short-secret")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.jwt.secret")
        .hasMessageNotContaining("my-real-but-short-secret");
  }
}
