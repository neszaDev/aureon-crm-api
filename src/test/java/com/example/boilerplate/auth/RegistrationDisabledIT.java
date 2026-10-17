package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Invite-only mode. Its own context (a different property), so it starts its own database. */
@TestPropertySource(properties = "app.security.registration-enabled=false")
class RegistrationDisabledIT extends WorkspaceTestSupport {
  @Test
  void registerIs403ButExistingAccountsStillSignIn() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(uniqueEmail(), PASSWORD))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Registration is disabled"));

    assertFalse(adminAccessToken().isBlank());
  }

  @Test
  void invitationLinksStillCreateAccounts() throws Exception {
    String admin = adminAccessToken();
    long w = createWorkspace(admin, "Invite only");
    String token =
        body(send(
                    admin,
                    post("/api/v1/workspaces/{w}/invitations", w),
                    Map.of("email", uniqueEmail(), "role", "SALES_REP"))
                .andExpect(status().isCreated()))
            .get("token")
            .asText();

    mvc.perform(
            post("/api/v1/invitations/{token}/register", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", PASSWORD))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").exists());
  }
}
