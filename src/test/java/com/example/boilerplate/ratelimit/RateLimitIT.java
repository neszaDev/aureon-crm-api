package com.example.boilerplate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs on a real Tomcat (not MockMvc) so X-Forwarded-For handling is the production one. Its own
 * context: limits are on and small here. Each test uses its own client IPs and emails.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.rate-limit.enabled=true",
      "app.rate-limit.login-per-ip.capacity=4",
      "app.rate-limit.login-failures-per-email.capacity=2",
      "app.rate-limit.register-per-ip.capacity=2"
    })
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class RateLimitIT {
  static final String PASSWORD = "a-secure-password";
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;

  @Test
  void registrationIsLimitedPerForwardedClientIp() throws Exception {
    assertThat(register(email(), "198.51.100.1").statusCode()).isEqualTo(201);
    assertThat(register(email(), "198.51.100.1").statusCode()).isEqualTo(201);

    var blocked = register(email(), "198.51.100.1");
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.headers().firstValue("Retry-After"))
        .hasValueSatisfying(v -> assertThat(Long.parseLong(v)).isPositive());
    assertThat(blocked.body()).contains("\"status\":429");

    // Another client behind the same proxy has its own budget.
    assertThat(register(email(), "198.51.100.2").statusCode()).isEqualTo(201);
  }

  @Test
  void failedLoginsBlockTheEmailFromAnyIp() throws Exception {
    String email = email();
    register(email, "198.51.100.10");

    assertThat(login(email, "wrong-password", "198.51.100.11").statusCode()).isEqualTo(401);
    assertThat(login(email, "wrong-password", "198.51.100.12").statusCode()).isEqualTo(401);

    // Blocked even with the right password, and from a fresh IP.
    assertThat(login(email, PASSWORD, "198.51.100.13").statusCode()).isEqualTo(429);
    // Other accounts are unaffected.
    String other = email();
    register(other, "198.51.100.14");
    assertThat(login(other, PASSWORD, "198.51.100.13").statusCode()).isEqualTo(200);
  }

  @Test
  void successfulLoginsDoNotCountAgainstTheEmail() throws Exception {
    String email = email();
    register(email, "198.51.100.20");
    for (String ip : new String[] {"198.51.100.21", "198.51.100.22", "198.51.100.23"})
      assertThat(login(email, PASSWORD, ip).statusCode()).isEqualTo(200);
  }

  @Test
  void loginAttemptsAreLimitedPerClientIp() throws Exception {
    for (int i = 0; i < 4; i++)
      assertThat(login(email(), "x", "198.51.100.30").statusCode()).isEqualTo(401);

    assertThat(login(email(), "x", "198.51.100.30").statusCode()).isEqualTo(429);
  }

  @Test
  void prependedForwardedEntriesCannotDodgeTheLimit() throws Exception {
    // Tomcat reads X-Forwarded-For right to left and stops at the first address that isn't a
    // trusted proxy, so a client can't pick a fresh IP by prepending entries.
    for (int i = 0; i < 4; i++) login(email(), "x", "203.0.113." + i + ", 198.51.100.40");

    assertThat(login(email(), "x", "198.51.100.40").statusCode()).isEqualTo(429);
  }

  private HttpResponse<String> register(String email, String clientIp) throws Exception {
    return post("/api/v1/auth/register", email, PASSWORD, clientIp);
  }

  private HttpResponse<String> login(String email, String password, String clientIp)
      throws Exception {
    return post("/api/v1/auth/login", email, password, clientIp);
  }

  private HttpResponse<String> post(String path, String email, String password, String clientIp)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", clientIp)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String email() {
    return "rl-" + UUID.randomUUID() + "@example.com";
  }
}
