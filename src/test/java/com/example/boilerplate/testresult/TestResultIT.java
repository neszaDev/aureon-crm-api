package com.example.boilerplate.testresult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TestResultIT extends AbstractIntegrationTest {
  private String token;

  @BeforeEach
  void newUser() throws Exception {
    token = registerAndGetAccessToken();
  }

  @Test
  void crudLifecycle() throws Exception {
    long id = create(token, "Blood pressure", "PASSED", 92.5);

    mvc.perform(auth(get("/api/v1/test-results/{id}", id), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testName").value("Blood pressure"))
        .andExpect(jsonPath("$.score").value(92.5));

    mvc.perform(
            auth(patch("/api/v1/test-results/{id}", id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Blood pressure", "FAILED", 45.0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"));

    mvc.perform(auth(delete("/api/v1/test-results/{id}", id), token))
        .andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/test-results/{id}", id), token)).andExpect(status().isNotFound());
  }

  @Test
  void listIsPaginatedAndSummaryCountsByStatus() throws Exception {
    create(token, "a", "PASSED", 90);
    create(token, "b", "PASSED", 80);
    create(token, "c", "FAILED", 10);

    mvc.perform(auth(get("/api/v1/test-results?page=0&size=2"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3));

    mvc.perform(auth(get("/api/v1/test-results/summary"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.byStatus[?(@.status=='PASSED')].count").value(2))
        .andExpect(jsonPath("$.byStatus[?(@.status=='FAILED')].count").value(1))
        .andExpect(jsonPath("$.byStatus[?(@.status=='PENDING')].count").value(0));
  }

  @Test
  void usersCannotSeeOrChangeEachOthersResults() throws Exception {
    long id = create(token, "private", "PENDING", 50);
    String other = registerAndGetAccessToken();

    mvc.perform(auth(get("/api/v1/test-results/{id}", id), other)).andExpect(status().isNotFound());
    mvc.perform(
            auth(patch("/api/v1/test-results/{id}", id), other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("hijack", "PASSED", 100)))
        .andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/test-results/{id}", id), other))
        .andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/test-results"), other))
        .andExpect(jsonPath("$.content.length()").value(0));

    // Still intact for the owner.
    mvc.perform(auth(get("/api/v1/test-results/{id}", id), token)).andExpect(status().isOk());
  }

  @Test
  void invalidBodyReturnsFieldErrors() throws Exception {
    mvc.perform(
            auth(post("/api/v1/test-results"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", "PASSED", 150)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.testName").exists())
        .andExpect(jsonPath("$.fieldErrors.score").exists());
  }

  private long create(String accessToken, String name, String status, double score)
      throws Exception {
    String response =
        mvc.perform(
                auth(post("/api/v1/test-results"), accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(name, status, score)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asLong();
  }

  private String body(String name, String status, double score) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "testName",
            name,
            "status",
            status,
            "score",
            score,
            "testedAt",
            "2026-08-25T10:30:00Z"));
  }

  private static MockHttpServletRequestBuilder auth(
      MockHttpServletRequestBuilder request, String accessToken) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
  }
}
