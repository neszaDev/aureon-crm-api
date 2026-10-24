package com.example.boilerplate.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ProfileIT extends AbstractIntegrationTest {
  private static final String NEW_PASSWORD = "another-password";

  @Test
  void putMeSetsAndClearsDisplayName() throws Exception {
    String token = registerAndGetAccessToken();

    putMe(token, "  Ada Lovelace ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));

    putMe(token, "   ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").doesNotExist());
    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(jsonPath("$.displayName").doesNotExist());
  }

  @Test
  void displayNameOver100CharsIs400() throws Exception {
    putMe(registerAndGetAccessToken(), "x".repeat(101))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.displayName").exists());
  }

  @Test
  void changePasswordRotatesSessions() throws Exception {
    String email = uniqueEmail();
    JsonNode registered = register(email);
    String firstRefresh = registered.get("refreshToken").asText();
    String accessToken = registered.get("accessToken").asText();
    JsonNode renewed =
        json.readTree(
            changePassword(accessToken, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", firstRefresh))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("refreshToken", renewed.get("refreshToken").asText()))))
        .andExpect(status().isOk());
    login(email, NEW_PASSWORD);
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(email, PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void wrongCurrentPasswordIs400WithFieldError() throws Exception {
    changePassword(registerAndGetAccessToken(), "not-the-password", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.fieldErrors.currentPassword").value("is incorrect"));
  }

  @Test
  void newPasswordTooShortIs400() throws Exception {
    changePassword(registerAndGetAccessToken(), PASSWORD, "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
  }

  @Test
  void newPasswordOver72BytesIs400EvenWhenUnder72Characters() throws Exception {
    // 30 Thai characters are 90 bytes in UTF-8, which BCrypt cannot hash.
    changePassword(registerAndGetAccessToken(), PASSWORD, "ก".repeat(30))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
  }

  @Test
  void passwordChangeNeedsASignedInUser() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions putMe(String accessToken, String displayName) throws Exception {
    return mvc.perform(
        put("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("displayName", displayName))));
  }

  private ResultActions changePassword(String accessToken, String current, String next)
      throws Exception {
    return mvc.perform(
        post("/api/v1/users/me/password")
            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
  }
}
