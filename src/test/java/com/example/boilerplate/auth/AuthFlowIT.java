package com.example.boilerplate.auth;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthFlowIT extends AbstractIntegrationTest {

  @Test
  void registerLoginRefreshLogoutLifecycle() throws Exception {
    String email = uniqueEmail();
    var registered = register(email);

    mvc.perform(
            get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registered.get("accessToken").asText())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$").value(org.hamcrest.Matchers.not(hasKey("passwordHash"))));

    // Login with different email casing: emails are stored and matched lower-case.
    String refresh =
        json.readTree(
                postJson("/api/v1/auth/login", new Credentials(email.toUpperCase(), PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("refreshToken")
            .asText();

    // Refresh rotates: the new token works, the old one is rejected.
    String rotated =
        json.readTree(
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("refreshToken")
            .asText();
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh))
        .andExpect(status().isUnauthorized());

    // Logout revokes the current refresh token.
    postJson("/api/v1/auth/logout", Map.of("refreshToken", rotated))
        .andExpect(status().isNoContent());
    postJson("/api/v1/auth/refresh", Map.of("refreshToken", rotated))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateRegistrationIsConflict() throws Exception {
    String email = uniqueEmail();
    register(email);
    postJson("/api/v1/auth/register", new Credentials(email.toUpperCase(), PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void wrongPasswordIsUnauthorized() throws Exception {
    String email = uniqueEmail();
    register(email);
    postJson("/api/v1/auth/login", new Credentials(email, "not-the-password"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void unknownEmailGivesSameErrorAsWrongPassword() throws Exception {
    postJson("/api/v1/auth/login", new Credentials(uniqueEmail(), PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void invalidRegistrationReturnsFieldErrors() throws Exception {
    postJson("/api/v1/auth/register", new Credentials("not-an-email", "123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.fieldErrors.email").exists())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  private ResultActions postJson(String path, Object body) throws Exception {
    return mvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }
}
