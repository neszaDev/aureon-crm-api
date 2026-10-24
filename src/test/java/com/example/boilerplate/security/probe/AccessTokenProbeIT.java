package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.auth.JwtService;
import com.example.boilerplate.support.AbstractIntegrationTest;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Security review probe (phase 4): only an access token the API signed, unexpired, of the user's
 * current version passes the filter, and the role always comes from the database. Forged tokens
 * here use the test profile's published placeholder secret, which the app refuses outside
 * local/dev/test (JwtSecretStartupTest).
 */
class AccessTokenProbeIT extends AbstractIntegrationTest {
  private static final byte[] SECRET =
      "development-only-change-this-secret-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);

  @Autowired UserRepository users;

  @Test
  void onlyTheApisOwnCurrentAccessTokensAuthenticate() throws Exception {
    String email = uniqueEmail();
    JsonNode tokens = register(email);
    User user = users.findByEmail(email).orElseThrow();
    Instant now = Instant.now();

    Map<String, String> refused = new java.util.LinkedHashMap<>();
    refused.put("unsigned (alg none)", unsigned(user));
    refused.put(
        "signed with another key",
        Jwts.builder()
            .subject(email)
            .claim(JwtService.USER_ID, user.getId())
            .claim(JwtService.TOKEN_VERSION, user.getTokenVersion())
            .expiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    "another-secret-of-at-least-thirty-two-bytes!"
                        .getBytes(StandardCharsets.UTF_8)))
            .compact());
    refused.put(
        "expired",
        Jwts.builder()
            .subject(email)
            .claim(JwtService.USER_ID, user.getId())
            .claim(JwtService.TOKEN_VERSION, user.getTokenVersion())
            .issuedAt(Date.from(now.minusSeconds(1200)))
            .expiration(Date.from(now.minusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET))
            .compact());
    refused.put(
        "a later version",
        Jwts.builder()
            .subject(email)
            .claim(JwtService.USER_ID, user.getId())
            .claim(JwtService.TOKEN_VERSION, user.getTokenVersion() + 1)
            .expiration(Date.from(now.plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET))
            .compact());
    refused.put(
        "no version",
        Jwts.builder()
            .subject(email)
            .claim(JwtService.USER_ID, user.getId())
            .expiration(Date.from(now.plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET))
            .compact());
    refused.put("the refresh token as a bearer", tokens.get("refreshToken").asText());
    refused.put("tampered payload", tamper(tokens.get("accessToken").asText()));

    List<String> wrong = new ArrayList<>();
    for (var e : refused.entrySet()) {
      int s =
          mvc.perform(
                  get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(e.getValue())))
              .andReturn()
              .getResponse()
              .getStatus();
      if (s != 401) wrong.add(e.getKey() + " -> " + s);
    }
    assertThat(wrong).isEmpty();

    // An access token is no refresh token.
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("refreshToken", tokens.get("accessToken").asText()))))
        .andExpect(status().isUnauthorized());

    // A validly signed token claiming ADMIN: the role is the database's, so still a USER.
    String claimsAdmin =
        Jwts.builder()
            .subject(email)
            .claim(JwtService.USER_ID, user.getId())
            .claim(JwtService.TOKEN_VERSION, user.getTokenVersion())
            .claim("role", "ADMIN")
            .expiration(Date.from(now.plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET))
            .compact();
    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(claimsAdmin)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(claimsAdmin)))
        .andExpect(status().isForbidden());
  }

  /** The claims of a real token under {@code alg: none}, without a signature. */
  private static String unsigned(User user) {
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    String header =
        b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        b64.encodeToString(
            ("{\"sub\":\""
                    + user.getEmail()
                    + "\",\"uid\":"
                    + user.getId()
                    + ",\"ver\":"
                    + user.getTokenVersion()
                    + ",\"role\":\"ADMIN\",\"exp\":"
                    + (Instant.now().getEpochSecond() + 600)
                    + "}")
                .getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".";
  }

  /** The token with its payload's role changed to ADMIN and the original signature kept. */
  private static String tamper(String token) {
    String[] parts = token.split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    String forged = payload.replace("\"USER\"", "\"ADMIN\"");
    assertThat(forged).isNotEqualTo(payload);
    return parts[0]
        + "."
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(forged.getBytes(StandardCharsets.UTF_8))
        + "."
        + parts[2];
  }
}
