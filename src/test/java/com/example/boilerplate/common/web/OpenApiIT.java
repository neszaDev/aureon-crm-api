package com.example.boilerplate.common.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

  /** Money is a string both ways, so the generated request types take one. */
  @Test
  void moneyInRequestsIsAString() throws Exception {
    String schemas = "$.components.schemas.";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(jsonPath(schemas + "CreateDealRequest.properties.amount.type").value("string"))
        .andExpect(jsonPath(schemas + "UpdateDealRequest.properties.amount.type").value("string"))
        .andExpect(jsonPath(schemas + "DealDraft.properties.amount.type").value("string"))
        .andExpect(
            jsonPath(schemas + "CreateLeadRequest.properties.estimatedValue.type").value("string"))
        .andExpect(
            jsonPath(schemas + "UpdateLeadRequest.properties.estimatedValue.type").value("string"));
  }

  /**
   * A response field that is never null is required; one that can be null is optional, since the
   * app's Jackson leaves nulls out. Requests keep what bean validation requires.
   */
  @Test
  void neverNullResponseFieldsAreRequired() throws Exception {
    String schemas = "$.components.schemas.";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(
            jsonPath(
                schemas + "StageResponse.required",
                containsInAnyOrder(
                    "id", "name", "position", "probability", "kind", "dealCount", "version")))
        .andExpect(
            jsonPath(
                schemas + "ApiError.required",
                containsInAnyOrder(
                    "timestamp", "status", "error", "message", "path", "fieldErrors")))
        .andExpect(
            jsonPath(
                schemas + "DealSummary.required",
                hasItems("id", "name", "amount", "stage", "stageEnteredAt", "updatedAt")))
        .andExpect(jsonPath(schemas + "DealSummary.required", not(hasItem("company"))))
        .andExpect(jsonPath(schemas + "LeadResponse.required", not(hasItem("convertedContact"))))
        .andExpect(
            jsonPath(schemas + "CreateDealRequest.required", containsInAnyOrder("name", "amount")));
  }
}
