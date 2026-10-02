package com.example.boilerplate.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.boilerplate.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class UserAdminIT extends AbstractIntegrationTest {
  @Autowired UserRepository users;
  private String admin;

  @BeforeEach
  void signInAsAdmin() throws Exception {
    admin = adminAccessToken();
  }

  @Test
  void bootstrapAdminHasTheAdminRole() throws Exception {
    mvc.perform(auth(get("/api/v1/users/me"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void regularUsersAreForbiddenWithApiError() throws Exception {
    String user = registerAndGetAccessToken();

    mvc.perform(auth(get("/api/v1/users"), user))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.path").value("/api/v1/users"));
    mvc.perform(auth(get("/api/v1/users/1"), user)).andExpect(status().isForbidden());
    mvc.perform(auth(delete("/api/v1/users/1"), user)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void listsAndSearchesUsersByEmail() throws Exception {
    String email = uniqueEmail();
    register(email);

    mvc.perform(auth(get("/api/v1/users").param("q", email.substring(5, 20).toUpperCase()), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value(email))
        .andExpect(jsonPath("$.content[0].role").value("USER"))
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    mvc.perform(auth(get("/api/v1/users").param("size", "1"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
    mvc.perform(auth(get("/api/v1/users").param("size", "1000"), admin))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getsOneUserOr404() throws Exception {
    String email = uniqueEmail();
    register(email);
    long id = idOf(email);

    mvc.perform(auth(get("/api/v1/users/{id}", id), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    mvc.perform(auth(get("/api/v1/users/{id}", Long.MAX_VALUE), admin))
        .andExpect(status().isNotFound());
  }

  @Test
  void promotingAUserGrantsAccessAndSignsThemOut() throws Exception {
    String email = uniqueEmail();
    String refreshToken = register(email).get("refreshToken").asText();
    long id = idOf(email);

    mvc.perform(patchJson(id, Map.of("role", "ADMIN"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    // Old sessions are revoked; a new sign-in carries the new role.
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
        .andExpect(status().isUnauthorized());
    String promoted = login(email, PASSWORD).get("accessToken").asText();
    mvc.perform(auth(get("/api/v1/users"), promoted)).andExpect(status().isOk());
  }

  @Test
  void demotedAdminLosesAccessBeforeTheirTokenExpires() throws Exception {
    String email = uniqueEmail();
    register(email);
    long id = idOf(email);
    mvc.perform(patchJson(id, Map.of("role", "ADMIN"), admin)).andExpect(status().isOk());
    String token = login(email, PASSWORD).get("accessToken").asText();

    mvc.perform(patchJson(id, Map.of("role", "USER"), admin)).andExpect(status().isOk());

    // The token still says ADMIN; the service checks the database.
    mvc.perform(auth(get("/api/v1/users"), token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Admin role required"));
  }

  @Test
  void changesEmailUnlessItIsTaken() throws Exception {
    String email = uniqueEmail();
    register(email);
    long id = idOf(email);
    String taken = uniqueEmail();
    register(taken);
    String renamed = uniqueEmail();

    mvc.perform(patchJson(id, Map.of("email", taken.toUpperCase()), admin))
        .andExpect(status().isConflict());
    mvc.perform(patchJson(id, Map.of("email", "not-an-email"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    mvc.perform(patchJson(id, Map.of("email", renamed.toUpperCase()), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(renamed));
    login(renamed, PASSWORD);
  }

  @Test
  void adminsCannotChangeOrDeleteThemselves() throws Exception {
    long self = idOf(ADMIN_EMAIL);

    mvc.perform(patchJson(self, Map.of("role", "USER"), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("You cannot change your own account"));
    mvc.perform(auth(delete("/api/v1/users/{id}", self), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("You cannot delete your own account"));
  }

  @Test
  void deletingAUserRemovesTheirAccountAndData() throws Exception {
    String email = uniqueEmail();
    var tokens = register(email);
    String token = tokens.get("accessToken").asText();
    long id = idOf(email);
    mvc.perform(
            auth(post("/api/v1/test-results"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "testName",
                            "x",
                            "status",
                            "PASSED",
                            "score",
                            1,
                            "testedAt",
                            "2026-08-25T10:30:00Z"))))
        .andExpect(status().isCreated());

    mvc.perform(auth(delete("/api/v1/users/{id}", id), admin)).andExpect(status().isNoContent());

    mvc.perform(auth(get("/api/v1/users/{id}", id), admin)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/users/me"), token)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("refreshToken", tokens.get("refreshToken").asText()))))
        .andExpect(status().isUnauthorized());
    mvc.perform(auth(delete("/api/v1/users/{id}", id), admin)).andExpect(status().isNotFound());
  }

  private long idOf(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private MockHttpServletRequestBuilder patchJson(long id, Object body, String token)
      throws Exception {
    return auth(patch("/api/v1/users/{id}", id), token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
  }

  private static MockHttpServletRequestBuilder auth(
      MockHttpServletRequestBuilder request, String accessToken) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
  }
}
