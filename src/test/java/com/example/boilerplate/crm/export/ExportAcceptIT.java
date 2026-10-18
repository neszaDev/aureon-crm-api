package com.example.boilerplate.crm.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A client that accepts nothing but {@code text/csv}, on a real Tomcat: MockMvc can't tell what the
 * container answers when the error body can't be written for the client's {@code Accept}. The test
 * profile caps an export at 3 rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class ExportAcceptIT {
  static final ObjectMapper JSON = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort int port;

  /**
   * Errors are JSON for any {@code Accept}: {@code GlobalExceptionHandler} sets the content type,
   * so Spring doesn't negotiate it. Before, {@code Accept: text/csv} made the handler fail with
   * {@code HttpMediaTypeNotAcceptableException}, the exception left Spring and the container's
   * error dispatch answered a 401.
   */
  @Test
  void errorsAreJsonForAClientAcceptingOnlyCsv() throws Exception {
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

    // Over the cap: the 400 with its code, whatever the Accept.
    for (String accept : List.of("text/csv", "application/json", "*/*")) {
      HttpResponse<String> over = export(companies + "/export", token, accept);
      assertThat(over.statusCode()).as(accept).isEqualTo(400);
      assertThat(over.headers().firstValue("Content-Type")).as(accept).contains("application/json");
      assertThat(JSON.readTree(over.body()).get("code").asText()).isEqualTo("EXPORT_TOO_LARGE");
    }

    // Another error, from the handler's not-found path.
    HttpResponse<String> missing = export(companies + "/999999", token, "text/csv");
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.headers().firstValue("Content-Type")).contains("application/json");
    assertThat(JSON.readTree(missing.body()).get("code").asText()).isEqualTo("NOT_FOUND");

    // The CSV itself is still sent to a client that accepts it (one row, under the cap).
    HttpResponse<String> csv = export(companies + "/export?q=Co%200", token, "text/csv");
    assertThat(csv.statusCode()).isEqualTo(200);
    assertThat(csv.headers().firstValue("Content-Type")).contains("text/csv;charset=UTF-8");
    assertThat(csv.body()).contains("Co 0").doesNotContain("Co 1");
  }

  private HttpResponse<String> export(String path, String token, String accept) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
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
