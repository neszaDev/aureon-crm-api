package com.example.boilerplate.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Access tokens are bound to the user id ({@code uid}), not to the email in {@code sub}: a token
 * whose email no longer belongs to its user, whose user is gone, or that predates the user's last
 * email, role or password change is refused (401).
 */
class TokenBindingIT extends WorkspaceTestSupport {

  /**
   * The security review's probe: a system admin parks a helper account's email and gives it to the
   * victim, so the helper's old token named the victim's email and resolved to the victim's
   * workspaces, where it could invite a lasting OWNER.
   */
  @Test
  void anEmailSwapDoesNotHandTheVictimsWorkspaceToAnotherToken() throws Exception {
    String victim = uniqueEmail();
    long ws = createWorkspace(tokenFor(victim), "Victim Co");
    long victimId = idOf(victim);
    String helper = uniqueEmail();
    String helperToken = tokenFor(helper);
    long helperId = idOf(helper);
    String admin = adminAccessToken();

    // The system ADMIN role grants nothing inside a workspace: a direct read is the usual 404.
    send(admin, get("/api/v1/workspaces/" + ws)).andExpect(status().isNotFound());

    // Step 1: the admin parks the helper's email and gives it to the victim (allowed).
    send(admin, patch("/api/v1/users/" + helperId), Map.of("email", "parked-" + helper))
        .andExpect(status().isOk());
    send(admin, patch("/api/v1/users/" + victimId), Map.of("email", helper))
        .andExpect(status().isOk());

    // Steps 2-4: the helper's old token names the victim's email now, and is refused.
    send(helperToken, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    send(helperToken, get("/api/v1/workspaces/" + ws)).andExpect(status().isUnauthorized());
    send(
            helperToken,
            post("/api/v1/workspaces/" + ws + "/invitations"),
            Map.of("email", "persist-" + UUID.randomUUID() + "@example.com", "role", "OWNER"))
        .andExpect(status().isUnauthorized());

    // The victim signs in with the new email and still owns the workspace.
    String renamed = login(helper, PASSWORD).get("accessToken").asText();
    send(renamed, get("/api/v1/workspaces/" + ws))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void aDeletedUsersTokenDoesNotAttachToWhoeverRegistersTheEmailAgain() throws Exception {
    String email = uniqueEmail();
    String oldToken = tokenFor(email);
    send(adminAccessToken(), delete("/api/v1/users/" + idOf(email)))
        .andExpect(status().isNoContent());
    String newToken = tokenFor(email);

    send(oldToken, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    send(newToken, get("/api/v1/users/me")).andExpect(status().isOk());
  }

  @Test
  void anAccessTokenIssuedBeforeAPasswordChangeIsRefused() throws Exception {
    String oldToken = registerAndGetAccessToken();
    waitForTheNextSecond();

    JsonNode renewed =
        body(
            send(
                    oldToken,
                    post("/api/v1/users/me/password"),
                    Map.of("currentPassword", PASSWORD, "newPassword", "another-password"))
                .andExpect(status().isOk()));

    send(oldToken, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    send(renewed.get("accessToken").asText(), get("/api/v1/users/me")).andExpect(status().isOk());
  }

  @Test
  void anAccessTokenIssuedBeforeARoleChangeIsRefused() throws Exception {
    String email = uniqueEmail();
    String oldToken = tokenFor(email);
    waitForTheNextSecond();

    send(adminAccessToken(), patch("/api/v1/users/" + idOf(email)), Map.of("role", "ADMIN"))
        .andExpect(status().isOk());

    send(oldToken, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    String promoted = login(email, PASSWORD).get("accessToken").asText();
    send(promoted, get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void aSessionKeepsWorkingAcrossRefresh() throws Exception {
    JsonNode registered = register(uniqueEmail());
    String access = registered.get("accessToken").asText();
    send(access, get("/api/v1/users/me")).andExpect(status().isOk());

    JsonNode refreshed =
        json.readTree(
            mvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of("refreshToken", registered.get("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    send(refreshed.get("accessToken").asText(), get("/api/v1/users/me")).andExpect(status().isOk());
    // Refreshing ends nothing: the earlier access token runs until it expires.
    send(access, get("/api/v1/users/me")).andExpect(status().isOk());
  }

  private long idOf(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  /**
   * A JWT's {@code iat} has whole seconds, so a token issued in the same second as a change is not
   * older than it; the tests that expect a refusal issue the token a second earlier.
   */
  private static void waitForTheNextSecond() throws InterruptedException {
    long second = Instant.now().getEpochSecond();
    while (Instant.now().getEpochSecond() == second) Thread.sleep(20);
  }
}
