package com.example.boilerplate.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * URLs Spring Security's firewall rejects are a 400 with the usual {@code ApiError} body, on a real
 * Tomcat: before, the rejection went to the container's error dispatch and came back as a 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class RequestRejectedIT {
  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;

  @Test
  void aRejectedUrlIsABadRequestWithAnApiError() throws Exception {
    for (String path :
        List.of(
            "//api/v1/users/me",
            "/api/v1/users/me;x=1",
            "/api/v1/%2e%2e/actuator/health",
            "/api/v1/invitations/secret-link-token;x=1")) {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(path).isEqualTo(400);
      assertThat(response.headers().firstValue("Content-Type"))
          .as(path)
          .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
      var body = JSON.readTree(response.body());
      assertThat(body.get("status").asInt()).isEqualTo(400);
      assertThat(body.get("message").asText()).isEqualTo("Malformed request");
      assertThat(response.body()).as(path).doesNotContain("secret-link-token");
    }
  }
}
