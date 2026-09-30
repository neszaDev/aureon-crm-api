package com.example.boilerplate.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import com.example.boilerplate.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** Access rules: public operational endpoints, 401 + ApiError for anything unauthenticated. */
class SecurityIT extends AbstractIntegrationTest {
  @Autowired UserRepository users;

  @Test
  void publicEndpointsNeedNoToken() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    // Tests disable metrics export, so the endpoint itself is absent (404) here; this only checks
    // security lets scrapers through. scripts/smoke-test.sh asserts the real 200 in prod.
    mvc.perform(get("/actuator/prometheus"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isNotEqualTo(401));
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void missingTokenIsUnauthorizedWithApiError() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("Authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    mvc.perform(get("/api/v1/test-results")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidTokenIsUnauthorized() throws Exception {
    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validTokenIsAccepted() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registerAndGetAccessToken())))
        .andExpect(status().isOk());
  }

  @Test
  void tokenOfDeletedUserIsUnauthorized() throws Exception {
    String email = uniqueEmail();
    String token = register(email).get("accessToken").asText();
    users.delete(users.findByEmail(email).orElseThrow());

    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void everyResponseCarriesARequestId() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(header().exists("X-Request-Id"));
  }
}
