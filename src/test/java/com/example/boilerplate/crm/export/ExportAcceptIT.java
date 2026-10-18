package com.example.boilerplate.crm.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A client that accepts nothing but {@code text/csv}, on a real Tomcat: MockMvc can't tell what the
 * container answers when the JSON error can't be written for it. The test profile caps an export at
 * 3 rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class ExportAcceptIT {
  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;

  /**
   * Documents what api.md says (Export). The handler's {@code ApiError} body has no media type the
   * client accepts, so the exception leaves Spring (the log shows {@code HttpMediaTypeNotAcceptable
   * Exception} from the exception handler) and the container answers: 401, not the 400 or a 406. If
   * that is fixed, change this test and the sentence in api.md.
   */
  @Test
  void anOverCapExportToAClientAcceptingOnlyCsvIs401() throws Exception {
    String email = "csv-" + UUID.randomUUID() + "@example.com";
    String token =
        JSON.readTree(
                post(
                        "/api/v1/auth/register",
                        null,
                        Map.of("email", email, "password", "a-secure-password"))
                    .body())
            .get("accessToken")
            .asText();
    JsonNode workspace =
        JSON.readTree(
            post(
                    "/api/v1/workspaces",
                    token,
                    Map.of("name", "Acme", "defaultCurrency", "EUR", "timezone", "Europe/Paris"))
                .body());
    String companies = "/api/v1/workspaces/" + workspace.get("id").asLong() + "/companies";
    for (int i = 0; i < 4; i++) post(companies, token, Map.of("name", "Co " + i));

    HttpResponse<String> json = export(companies, token, "application/json");
    assertThat(json.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(json.body()).get("code").asText()).isEqualTo("EXPORT_TOO_LARGE");

    HttpResponse<String> csv = export(companies, token, "text/csv");
    assertThat(csv.statusCode()).isEqualTo(401);
    assertThat(JSON.readTree(csv.body()).get("message").asText())
        .isEqualTo("Authentication required");
  }

  private HttpResponse<String> export(String companies, String token, String accept)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + companies + "/export"))
            .header("Authorization", "Bearer " + token)
            .header("Accept", accept)
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** POSTs {@code body} as JSON, the call being expected to create something (201). */
  private HttpResponse<String> post(String path, String token, Object body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    if (token != null) request.header("Authorization", "Bearer " + token);
    HttpResponse<String> response =
        http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("POST " + path).isEqualTo(201);
    return response;
  }
}
