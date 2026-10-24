package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/**
 * Security review probe (phase 4): the account and invitation flows, with the log levels of prod
 * (root INFO), of the local/dev profiles (the application at DEBUG, Hibernate's SQL) and with the
 * framework's web and security DEBUG on top, never write a password, an access or refresh token, or
 * the JWT secret to the logs. An invitation token stays out too, except under framework DEBUG,
 * which logs request URIs (documented: keep it off in prod, docs/configuration.md).
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretLoggingProbeIT extends WorkspaceTestSupport {
  private static final String JWT_SECRET =
      "development-only-change-this-secret-to-at-least-32-bytes";
  private final LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());

  @AfterEach
  void restoreLevels() {
    logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.WARN);
    for (String name :
        List.of(
            "com.example.boilerplate",
            "org.hibernate.SQL",
            "org.springframework.web",
            "org.springframework.security")) {
      logging.setLogLevel(name, null);
    }
  }

  @Test
  void prodLevelsLogNoSecretNorInvitationToken(CapturedOutput output) throws Exception {
    logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.INFO);
    List<String> secrets = flows();
    assertThat(output.getAll()).contains("password.change"); // the account events did log
    assertNoneLogged(output, secrets);
  }

  @Test
  void devProfileLevelsLogNoSecretNorInvitationToken(CapturedOutput output) throws Exception {
    logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.INFO);
    logging.setLogLevel("com.example.boilerplate", LogLevel.DEBUG);
    logging.setLogLevel("org.hibernate.SQL", LogLevel.DEBUG);
    List<String> secrets = flows();
    assertThat(output.getAll()).contains("insert into users"); // SQL logging was on
    assertNoneLogged(output, secrets);
  }

  @Test
  void frameworkDebugLogsNoPasswordNorSessionToken(CapturedOutput output) throws Exception {
    logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.INFO);
    logging.setLogLevel("com.example.boilerplate", LogLevel.DEBUG);
    logging.setLogLevel("org.springframework.web", LogLevel.DEBUG);
    logging.setLogLevel("org.springframework.security", LogLevel.DEBUG);
    List<String> secrets = flows();
    assertThat(output.getAll()).contains("RegisterRequest[email="); // bodies were logged
    // The last one is the invitation token: in the URI, which framework DEBUG logs.
    assertNoneLogged(output, secrets.subList(0, secrets.size() - 1));
  }

  private static void assertNoneLogged(CapturedOutput output, List<String> secrets) {
    List<String> found = new ArrayList<>();
    for (String s : secrets) if (output.getAll().contains(s)) found.add(s);
    assertThat(found).as("secrets found in the log output").isEmpty();
  }

  /**
   * Register, sign in (right and wrong), refresh, replay, change the password (wrong and right), an
   * admin's email change, invite and register through the link, sign out. Returns every secret
   * used, the invitation token last.
   */
  private List<String> flows() throws Exception {
    String tag = UUID.randomUUID().toString();
    String password = "pw-" + tag;
    String wrongPassword = "wrong-" + tag;
    String newPassword = "new-" + tag;
    String invitedPassword = "inv-" + tag;
    String email = uniqueEmail();

    JsonNode registered =
        auth("/api/v1/auth/register", Map.of("email", email, "password", password), 201);
    auth("/api/v1/auth/login", Map.of("email", email, "password", wrongPassword), 401);
    JsonNode login = auth("/api/v1/auth/login", Map.of("email", email, "password", password), 200);
    String refresh = login.get("refreshToken").asText();
    JsonNode rotated = auth("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 200);
    auth("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401); // a replay
    String access = rotated.get("accessToken").asText();
    send(
            access,
            post("/api/v1/users/me/password"),
            Map.of("currentPassword", wrongPassword, "newPassword", newPassword))
        .andExpect(status().isBadRequest());
    JsonNode changed =
        body(
            send(
                    access,
                    post("/api/v1/users/me/password"),
                    Map.of("currentPassword", password, "newPassword", newPassword))
                .andExpect(status().isOk()));
    long userId = users.findByEmail(email).orElseThrow().getId();
    send(adminAccessToken(), patch("/api/v1/users/{id}", userId), Map.of("email", uniqueEmail()))
        .andExpect(status().isOk());

    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Logs");
    String invited = uniqueEmail();
    String link =
        body(send(
                    owner,
                    post("/api/v1/workspaces/{w}/invitations", w),
                    Map.of("email", invited, "role", "SALES_REP"))
                .andExpect(status().isCreated()))
            .get("token")
            .asText();
    mvc.perform(get("/api/v1/invitations/{t}", link)).andExpect(status().isOk());
    JsonNode joined =
        body(
            mvc.perform(
                    post("/api/v1/invitations/{t}/register", link)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", invitedPassword))))
                .andExpect(status().isCreated()));
    auth("/api/v1/auth/logout", Map.of("refreshToken", joined.get("refreshToken").asText()), 204);

    return List.of(
        password,
        wrongPassword,
        newPassword,
        invitedPassword,
        registered.get("accessToken").asText(),
        registered.get("refreshToken").asText(),
        login.get("accessToken").asText(),
        refresh,
        access,
        rotated.get("refreshToken").asText(),
        changed.get("accessToken").asText(),
        changed.get("refreshToken").asText(),
        joined.get("accessToken").asText(),
        joined.get("refreshToken").asText(),
        JWT_SECRET,
        link);
  }

  private JsonNode auth(String path, Map<String, String> body, int expected) throws Exception {
    String text =
        mvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return text.isEmpty() ? json.nullNode() : json.readTree(text);
  }
}
