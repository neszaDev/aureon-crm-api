package com.example.boilerplate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.example.boilerplate.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;
  @Autowired UserRepository users;

  @Test
  void registrationIsLimitedPerForwardedClientIp() throws Exception {
    assertThat(register(email(), "198.51.100.1").statusCode()).isEqualTo(201);
    assertThat(register(email(), "198.51.100.1").statusCode()).isEqualTo(201);

    var blocked = register(email(), "198.51.100.1");
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.headers().firstValue("Retry-After"))
        .hasValueSatisfying(v -> assertThat(Long.parseLong(v)).isPositive());
    assertThat(blocked.body()).contains("\"status\":429", "\"code\":\"RATE_LIMITED\"");

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

  /** Registering through an invitation link takes from the same per-IP budget as registration. */
  @Test
  void invitationRegistrationSharesThePerIpRegistrationLimit() throws Exception {
    String ip = "198.51.100.50";
    assertThat(register(email(), ip).statusCode()).isEqualTo(201);
    // The limit is taken before the link is looked up, so an unknown link still counts.
    String link = Tokens.random();
    assertThat(registerThroughLink(link, ip).statusCode()).isEqualTo(404);

    var blocked = registerThroughLink(link, ip);
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.headers().firstValue("Retry-After"))
        .hasValueSatisfying(v -> assertThat(Long.parseLong(v)).isPositive());
    assertThat(blocked.body())
        .contains("\"path\":\"/api/v1/invitations/{token}/register\"")
        .doesNotContain(link);
  }

  /** The failed sign-in that blocks an email is logged once, by user id (M3). */
  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void theFailedSignInThatBlocksAnEmailIsLoggedOnce(CapturedOutput out) throws Exception {
    String email = email();
    register(email, "198.51.100.70");
    String line =
        "Account event event=sign-in.lockout actorId=null targetId="
            + users.findByEmail(email).orElseThrow().getId()
            + " fields=[]";

    login(email, "wrong-password", "198.51.100.71");
    assertThat(out.getOut()).doesNotContain(line);
    login(email, "wrong-password", "198.51.100.72");
    assertThat(login(email, PASSWORD, "198.51.100.73").statusCode()).isEqualTo(429);

    assertThat(out.getOut()).containsOnlyOnce(line).doesNotContain(email);
  }

  /** Guessing the current password while signed in counts as a failed sign-in, and vice versa. */
  @Test
  void wrongCurrentPasswordsShareThePerEmailFailureLimitWithLogin() throws Exception {
    String email = email();
    String accessToken =
        JSON.readTree(register(email, "198.51.100.60").body()).get("accessToken").asText();

    assertThat(login(email, "wrong-password", "198.51.100.61").statusCode()).isEqualTo(401);
    assertThat(changePassword(accessToken, "wrong-password").statusCode()).isEqualTo(400);

    assertThat(changePassword(accessToken, PASSWORD).statusCode()).isEqualTo(429);
    assertThat(login(email, PASSWORD, "198.51.100.62").statusCode()).isEqualTo(429);
  }

  private HttpResponse<String> register(String email, String clientIp) throws Exception {
    return post("/api/v1/auth/register", email, PASSWORD, clientIp);
  }

  private HttpResponse<String> login(String email, String password, String clientIp)
      throws Exception {
    return post("/api/v1/auth/login", email, password, clientIp);
  }

  private HttpResponse<String> registerThroughLink(String link, String clientIp) throws Exception {
    return send(
        "/api/v1/invitations/" + link + "/register",
        "{\"password\":\"" + PASSWORD + "\"}",
        "X-Forwarded-For",
        clientIp);
  }

  private HttpResponse<String> changePassword(String accessToken, String currentPassword)
      throws Exception {
    return send(
        "/api/v1/users/me/password",
        "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"another-password\"}",
        "Authorization",
        "Bearer " + accessToken);
  }

  private HttpResponse<String> post(String path, String email, String password, String clientIp)
      throws Exception {
    return send(
        path,
        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
        "X-Forwarded-For",
        clientIp);
  }

  private HttpResponse<String> send(String path, String json, String header, String value)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header(header, value)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String email() {
    return "rl-" + UUID.randomUUID() + "@example.com";
  }
}
