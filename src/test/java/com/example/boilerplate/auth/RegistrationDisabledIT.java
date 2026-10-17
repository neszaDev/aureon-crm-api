package com.example.boilerplate.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Invite-only mode. Its own context (a different property), so it starts its own database. */
@TestPropertySource(properties = "app.security.registration-enabled=false")
class RegistrationDisabledIT extends AbstractIntegrationTest {
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
}
