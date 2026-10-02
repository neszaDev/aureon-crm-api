package com.example.boilerplate.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for {@code *IT} classes: full application context, real security filter chain, and a shared
 * PostgreSQL container. All subclasses share one cached context, so tests must not rely on an empty
 * database; use {@link #uniqueEmail()} instead of cleaning up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
public abstract class AbstractIntegrationTest {
  protected static final String PASSWORD = "a-secure-password";

  /** Created by AdminBootstrap from application-test.yml. */
  protected static final String ADMIN_EMAIL = "admin@boilerplate.test";

  protected static final String ADMIN_PASSWORD = "test-admin-password";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;

  protected static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  /** Registers a new user and returns the token response body. */
  protected JsonNode register(String email) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Credentials(email, PASSWORD))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  /** Signs in and returns the token response body. */
  protected JsonNode login(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Credentials(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  protected String adminAccessToken() throws Exception {
    return login(ADMIN_EMAIL, ADMIN_PASSWORD).get("accessToken").asText();
  }

  protected String registerAndGetAccessToken() throws Exception {
    return register(uniqueEmail()).get("accessToken").asText();
  }

  protected static String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  public record Credentials(String email, String password) {}
}
