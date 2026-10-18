package com.example.boilerplate.auth;

import com.example.boilerplate.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final Duration ttl;

  static final int MIN_SECRET_BYTES = 32; // HS256 needs a 256-bit key

  /** The user's id: what a token is bound to ({@code sub} keeps the email). */
  public static final String USER_ID = "uid";

  /**
   * Published in this repository (application-*.yml, env/.env.example): anyone can sign with them.
   */
  static final Set<String> PLACEHOLDER_SECRETS =
      Set.of(
          "development-only-change-this-secret-to-at-least-32-bytes",
          "replace-with-a-random-secret-at-least-32-bytes-long");

  public JwtService(AuthProperties properties, Environment environment) {
    if (PLACEHOLDER_SECRETS.contains(properties.jwt().secret())
        && !environment.acceptsProfiles(Profiles.of("(local | dev | test) & !prod")))
      // Never include the value: this message ends up in startup logs.
      throw new IllegalStateException(
          "app.security.jwt.secret (JWT_SECRET) is a published placeholder, accepted only under"
              + " the local, dev or test profile; set a random one (openssl rand -hex 32)");
    byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < MIN_SECRET_BYTES) {
      // Never include the value: this message ends up in startup logs.
      throw new IllegalStateException(
          "app.security.jwt.secret (JWT_SECRET) must be at least "
              + MIN_SECRET_BYTES
              + " bytes, got "
              + secret.length);
    }
    this.key = Keys.hmacShaKeyFor(secret);
    this.ttl = properties.jwt().accessTokenTtl();
  }

  public String issue(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getEmail())
        .claim(USER_ID, user.getId())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  public long expiresInSeconds() {
    return ttl.toSeconds();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
