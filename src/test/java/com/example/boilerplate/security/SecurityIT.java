package com.example.boilerplate.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Pins the current access rules. Unauthenticated requests to protected endpoints currently get 403
 * (no AuthenticationEntryPoint is configured); change these assertions deliberately if that becomes
 * 401.
 */
class SecurityIT extends AbstractIntegrationTest {

  @Test
  void publicEndpointsNeedNoToken() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void protectedEndpointsRejectMissingToken() throws Exception {
    mvc.perform(get("/api/v1/users/me")).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/test-results")).andExpect(status().isForbidden());
    mvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
  }

  @Test
  void protectedEndpointsRejectInvalidToken() throws Exception {
    mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
        .andExpect(status().isForbidden());
  }

  @Test
  void validTokenIsAccepted() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registerAndGetAccessToken())))
        .andExpect(status().isOk());
  }

  @Test
  void everyResponseCarriesARequestId() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(header().exists("X-Request-Id"));
  }
}
