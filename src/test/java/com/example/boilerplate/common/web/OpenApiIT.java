package com.example.boilerplate.common.web;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** The spec the frontend generates its types from describes the error body and its codes. */
class OpenApiIT extends AbstractIntegrationTest {
  @Test
  void theErrorBodyIsInTheSpec() throws Exception {
    String apiError = "$.components.schemas.ApiError.properties";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(apiError + ".code.enum", hasItems("VERSION_CONFLICT", "LAST_OWNER")))
        .andExpect(jsonPath(apiError + ".fieldErrors.additionalProperties.type").value("string"))
        .andExpect(jsonPath(apiError + ".status.type").value("integer"))
        .andExpect(jsonPath(apiError + ".message.type").value("string"));
  }
}
