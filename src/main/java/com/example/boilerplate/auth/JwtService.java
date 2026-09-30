package com.example.boilerplate.auth;

import com.example.boilerplate.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final Duration ttl;

  static final int MIN_SECRET_BYTES = 32; // HS256 needs a 256-bit key

  public JwtService(AuthProperties properties) {
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
