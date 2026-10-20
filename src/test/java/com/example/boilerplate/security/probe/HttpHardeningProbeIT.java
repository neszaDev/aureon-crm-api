package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Security review probe (phase 4), on a real Tomcat (the same context as RequestRejectedIT): CORS
 * answers only the configured origins and never allows credentials, the API sets no cookie, its
 * responses carry the security headers, and the container's own error pages and TRACE give nothing
 * away. The test profile's origins are application.yml's defaults (localhost:3000 and :8080).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class HttpHardeningProbeIT {
  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;

  private HttpRequest.Builder at(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private HttpResponse<String> send(HttpRequest r) throws Exception {
    return http.send(r, HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest preflight(String origin) {
    return at("/api/v1/workspaces")
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .header("Origin", origin)
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,content-type")
        .build();
  }

  @Test
  void corsAnswersOnlyTheConfiguredOriginsWithoutCredentials() throws Exception {
    HttpResponse<String> allowed = send(preflight("http://localhost:3000"));
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
        .hasValue("http://localhost:3000");
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

    for (String origin :
        List.of("https://evil.example", "null", "http://localhost:3000.evil.example")) {
      HttpResponse<String> refused = send(preflight(origin));
      assertThat(refused.statusCode()).as(origin).isEqualTo(403);
      assertThat(refused.headers().firstValue("Access-Control-Allow-Origin")).as(origin).isEmpty();
      HttpResponse<String> simple =
          send(at("/actuator/health").header("Origin", origin).GET().build());
      assertThat(simple.headers().firstValue("Access-Control-Allow-Origin")).as(origin).isEmpty();
    }
  }

  @Test
  void theApiSetsNoCookieAndSendsTheSecurityHeaders() throws Exception {
    String body =
        "{\"email\":\"cookie-"
            + UUID.randomUUID()
            + "@example.com\",\"password\":\"a-secure-password\"}";
    HttpResponse<String> registered =
        send(
            at("/api/v1/auth/register")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    assertThat(registered.statusCode()).isEqualTo(201);
    String token = JSON.readTree(registered.body()).get("accessToken").asText();
    HttpResponse<String> me =
        send(at("/api/v1/users/me").header("Authorization", "Bearer " + token).GET().build());
    HttpResponse<String> anonymous = send(at("/api/v1/users/me").GET().build());

    for (HttpResponse<String> r : List.of(registered, me, anonymous)) {
      assertThat(r.headers().allValues("Set-Cookie")).as(r.uri().getPath()).isEmpty();
      assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
      assertThat(r.headers().firstValue("X-Frame-Options")).hasValue("DENY");
      assertThat(r.headers().firstValue("Cache-Control"))
          .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
    }
  }

  @Test
  void theContainersErrorsAndTraceGiveNothingAway() throws Exception {
    String secret = "Bearer secret-" + UUID.randomUUID();
    // An encoded NUL never reaches the application: Tomcat's own 400 page.
    HttpResponse<String> nul =
        send(at("/api/v1/users/%00").header("Authorization", secret).GET().build());
    assertThat(nul.statusCode()).isEqualTo(400);
    assertThat(nul.body()).doesNotContain("Tomcat/").doesNotContain("\tat ").doesNotContain(secret);
    assertThat(nul.headers().firstValue("Server")).isEmpty();

    HttpResponse<String> trace =
        send(
            at("/api/v1/users/me")
                .header("Authorization", secret)
                .method("TRACE", HttpRequest.BodyPublishers.noBody())
                .build());
    // Spring Security's firewall allows no TRACE: the usual 400, nothing echoed.
    assertThat(trace.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(trace.body()).get("message").asText()).isEqualTo("Malformed request");
    assertThat(trace.body()).doesNotContain(secret).doesNotContain("Tomcat/");
  }
}
