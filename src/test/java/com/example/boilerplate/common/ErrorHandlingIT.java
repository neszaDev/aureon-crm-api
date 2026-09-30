package com.example.boilerplate.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Client mistakes must map to 4xx with the ApiError shape, never to 500. */
class ErrorHandlingIT extends AbstractIntegrationTest {
  private String auth;

  @BeforeEach
  void login() throws Exception {
    auth = bearer(registerAndGetAccessToken());
  }

  @Test
  void outOfRangeQueryParamIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/test-results?size=1000").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void nonNumericPathVariableIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/test-results/abc").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isBadRequest());
  }

  @Test
  void malformedJsonIsBadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/test-results")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/v1/test-results"));
  }

  @Test
  void unknownRouteIsNotFound() throws Exception {
    mvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void unsupportedMethodIsMethodNotAllowed() throws Exception {
    mvc.perform(put("/api/v1/test-results").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isMethodNotAllowed());
  }
}
