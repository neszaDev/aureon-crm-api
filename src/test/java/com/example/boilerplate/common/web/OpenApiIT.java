package com.example.boilerplate.common.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

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
                hasItems(
                    "id", "name", "amount", "stage", "stageEnteredAt", "signatures", "updatedAt")))
        .andExpect(jsonPath(schemas + "DealSummary.required", not(hasItem("company"))))
        .andExpect(jsonPath(schemas + "StageSignature.required", containsInAnyOrder("stageId")))
        .andExpect(jsonPath(schemas + "LeadResponse.required", not(hasItem("convertedContact"))))
        .andExpect(
            jsonPath(schemas + "CreateDealRequest.required", containsInAnyOrder("name", "amount")));
  }

  /**
   * Spring's {@code Page} always has these fields, but its schema lists them as optional: the
   * wrappers say what they always carry. Other schemas that start with "Page" are none of its
   * business.
   */
  @Test
  void pageWrappersHaveTheirFieldsRequired() throws Exception {
    String schemas = "$.components.schemas.";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(
            jsonPath(
                schemas + "PageDealSummary.required",
                containsInAnyOrder(
                    "content",
                    "totalElements",
                    "totalPages",
                    "number",
                    "size",
                    "first",
                    "last",
                    "empty")))
        .andExpect(jsonPath(schemas + "PageableObject.required").doesNotExist());
  }

  /**
   * An export's 200 is a CSV text, not springdoc's default wildcard-typed byte string, so the
   * generated client types it as a string; the errors stay JSON (no {@code produces}).
   */
  @Test
  void exportsAreCsvInTheSpec() throws Exception {
    ResultActions spec = mvc.perform(get("/v3/api-docs"));
    for (String list : List.of("companies", "contacts", "leads", "deals")) {
      String ok =
          "$.paths['/api/v1/workspaces/{workspaceId}/" + list + "/export'].get.responses['200']";
      spec.andExpect(jsonPath(ok + ".content['text/csv'].schema.type").value("string"))
          .andExpect(jsonPath(ok + ".content", not(hasKey("*/*"))));
    }
  }

  /** The dashboard and the reports say what they always carry, and what can be absent. */
  @Test
  void insightSchemasSayWhatIsRequired() throws Exception {
    String schemas = "$.components.schemas.";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(
            jsonPath(
                schemas + "DashboardResponse.required",
                containsInAnyOrder(
                    "period",
                    "counts",
                    "openPipeline",
                    "won",
                    "lost",
                    "byStage",
                    "recentActivities",
                    "myUpcomingTasks",
                    "myOverdueTasks",
                    "recentChanges")))
        .andExpect(jsonPath(schemas + "AuditEventResponse.required", not(hasItem("actor"))))
        .andExpect(jsonPath(schemas + "AuditEventResponse.required", hasItem("action")))
        .andExpect(jsonPath(schemas + "OwnerBucket.required", not(hasItem("owner"))))
        .andExpect(jsonPath(schemas + "OwnerBucket.required", hasItems("count", "totals")))
        .andExpect(jsonPath(schemas + "WonLostReport.required", hasItems("period", "months")))
        .andExpect(jsonPath(schemas + "FunnelReport.required", hasItems("period", "stages")))
        .andExpect(jsonPath(schemas + "ActivityReport.required", hasItems("period", "byUser")));
  }
}
