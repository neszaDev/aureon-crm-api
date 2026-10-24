package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Security review probe (phase 4): the application as it runs under the {@code prod} profile, on a
 * real Tomcat. Nothing but health, info (and prometheus) is exposed and health shows no details;
 * the API docs and Swagger UI are off; the demo data never seeds, even asked to; CORS has no
 * default origins, only the configured one. Logging is left to the test JVM (the prod logback setup
 * would switch every later test to JSON at INFO): JSON logs are the smoke test's ({@code make
 * smoke}).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "DB_URL=jdbc:postgresql://replaced-by-the-service-connection/x",
      "DB_USERNAME=x",
      "DB_PASSWORD=x",
      "JWT_SECRET=4f9a1c0e7b2d5a8f3c6e9b1d4a7f0c3e6b9d2a5f8c1e4b7d0a3f6c9e2b5d8a1f",
      "CORS_ALLOWED_ORIGINS=https://crm.example",
      "app.demo.enabled=true",
      "app.demo.password=prod-demo-password",
      "app.admin.email=admin@prod.test",
      "app.admin.password=prod-admin-password-1"
    })
@ActiveProfiles("prod")
@Import(PostgresTestcontainersConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProdProfileRuntimeProbeIT {
  private static final String NO_LOGGING = "org.springframework.boot.logging.LoggingSystem";

  static {
    // Before the context starts: keep this context from re-initializing the JVM's logging.
    System.setProperty(NO_LOGGING, "none");
  }

  @AfterAll
  static void restoreLogging() {
    System.clearProperty(NO_LOGGING);
  }

  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;
  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate jdbc;

  private HttpResponse<String> get(String path, String token) throws Exception {
    var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (token != null) r.header("Authorization", "Bearer " + token);
    return http.send(r.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String adminToken() throws Exception {
    var r =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"admin@prod.test\",\"password\":\"prod-admin-password-1\"}"))
            .build();
    HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString());
    assertThat(res.statusCode()).isEqualTo(200);
    return JSON.readTree(res.body()).get("accessToken").asText();
  }

  @Test
  void onlyHealthInfoAndPrometheusAreExposedAndHealthHasNoDetails() throws Exception {
    String admin = adminToken();
    HttpResponse<String> health = get("/actuator/health", null);
    assertThat(health.statusCode()).isEqualTo(200);
    JsonNode h = JSON.readTree(health.body());
    assertThat(h.get("status").asText()).isEqualTo("UP");
    assertThat(h.has("components")).isFalse();
    assertThat(h.has("details")).isFalse();
    assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    assertThat(get("/actuator/info", null).body())
        .doesNotContainIgnoringCase("secret")
        .doesNotContainIgnoringCase("password");
    // /actuator/prometheus is public by design (restrict it at the network or ingress); a test
    // context has no metrics export, so the smoke test checks that one.

    List<String> exposed = new ArrayList<>();
    for (String endpoint :
        List.of(
            "metrics",
            "env",
            "beans",
            "configprops",
            "heapdump",
            "threaddump",
            "loggers",
            "mappings",
            "conditions",
            "scheduledtasks",
            "caches",
            "shutdown",
            "flyway",
            "sbom")) {
      for (String token : new String[] {null, admin}) {
        int status = get("/actuator/" + endpoint, token).statusCode();
        if (status != (token == null ? 401 : 404)) {
          exposed.add(endpoint + (token == null ? " anonymous" : " as admin") + " -> " + status);
        }
      }
    }
    assertThat(exposed).isEmpty();
  }

  @Test
  void theApiDocsAreOffAndTheDemoDataNeverSeeds() throws Exception {
    for (String path :
        List.of(
            "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html", "/swagger-ui.html")) {
      HttpResponse<String> docs = get(path, null);
      // 404 under the permitAll matchers, 401 next to them: never served.
      assertThat(docs.statusCode()).as(path).isIn(401, 404);
      assertThat(docs.body())
          .as(path)
          .doesNotContain("\"openapi\"")
          .doesNotContainIgnoringCase("<html");
    }
    assertThat(context.getBeanNamesForType(com.example.boilerplate.crm.demo.DemoDataSeeder.class))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE '%@demo.test'", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM workspaces", Long.class)).isZero();
  }

  @Test
  void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
    for (String origin :
        List.of("https://crm.example", "http://localhost:3000", "http://localhost:8080")) {
      var r =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
              .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
              .header("Origin", origin)
              .header("Access-Control-Request-Method", "POST")
              .build();
      HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString());
      boolean configured = origin.equals("https://crm.example");
      assertThat(res.statusCode()).as(origin).isEqualTo(configured ? 200 : 403);
      assertThat(res.headers().firstValue("Access-Control-Allow-Origin").isPresent())
          .as(origin)
          .isEqualTo(configured);
    }
  }
}
