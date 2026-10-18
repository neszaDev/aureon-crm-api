package com.example.boilerplate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.crm.settings.LeadSource;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import com.example.boilerplate.crm.settings.PipelineStageRepository;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.ResultActions;

/** Every rule a client may run into answers with its status and its machine-readable code. */
class ErrorCodeIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";
  static final String STAGES = W + "/pipeline-stages";
  static final String SOURCES = W + "/lead-sources";
  static final String LEADS = W + "/leads";
  static final String INVITATIONS = W + "/invitations";
  static final String MEMBER = W + "/members/{userId}";

  @Autowired LeadSourceRepository sources;
  @Autowired PipelineStageRepository stages;
  @Autowired GlobalExceptionHandler handler;

  @Test
  void requestErrors() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long won = stageId(owner, w, "Won");

    // Bean validation, a service's field check, a query parameter.
    expect(send(owner, post(STAGES, w), Map.of("name", "Demo")), 400, "VALIDATION_FAILED");
    expect(
        send(owner, post(STAGES, w), Map.of("name", " ", "probability", 10)),
        400,
        "VALIDATION_FAILED");
    expect(send(owner, get(LEADS, w).param("size", "1000")), 400, "VALIDATION_FAILED");
    expect(send(owner, post(SOURCES, w), Map.of("name", "Trade\u0000show")), 400, "TEXT_INVALID");
    // A time the database can't hold (PostgreSQL ends in the year 294276).
    expect(
        send(
            owner,
            post(W + "/tasks", w),
            Map.of("title", "Far", "dueAt", "+300000-01-01T00:00:00Z")),
        400,
        "TEXT_INVALID");
    expect(
            send(owner, put(STAGES + "/order", w), Map.of("stageIds", List.of(won))),
            400,
            "PIPELINE_CHANGED")
        .andExpect(jsonPath("$.fieldErrors.stageIds").exists());

    // Over the export cap (3 rows in the test profile).
    for (int i = 1; i <= 4; i++) {
      send(owner, post(W + "/companies", w), Map.of("name", "Row " + i))
          .andExpect(status().isCreated());
    }
    expect(send(owner, get(W + "/companies/export", w)), 400, "EXPORT_TOO_LARGE");

    expect(send(rep, post(STAGES, w), Map.of("name", "Demo", "probability", 10)), 403, "FORBIDDEN");
    expect(send(rep, get("/api/v1/users")), 403, "FORBIDDEN");

    expect(send(owner, delete(STAGES + "/{id}", w, 999_999_999L)), 404, "NOT_FOUND");
    expect(send(tokenFor(uniqueEmail()), get(W, w)), 404, "NOT_FOUND");

    expect(
        send(
            owner,
            put(STAGES + "/{id}", w, won),
            Map.of("name", "W", "probability", 100, "version", 9)),
        409,
        "VERSION_CONFLICT");
  }

  @Test
  void accountRules() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    createWorkspace(token, "Acme");
    String admin = adminAccessToken();

    expect(
        mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(email, PASSWORD)))),
        409,
        "EMAIL_TAKEN");
    expect(send(admin, delete("/api/v1/users/{id}", userId(email))), 409, "ACCOUNT_OWNS_WORKSPACE");
    expect(send(admin, delete("/api/v1/users/{id}", userId(ADMIN_EMAIL))), 409, "OWN_ACCOUNT");
  }

  @Test
  void memberAndInvitationRules() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String adminEmail = uniqueEmail();
    String admin = tokenFor(adminEmail);
    addMember(w, adminEmail, WorkspaceRole.ADMIN);
    long ownerId = userId(ownerEmail);

    expect(send(owner, delete(MEMBER, w, ownerId)), 409, "LAST_OWNER");
    expect(send(admin, put(MEMBER, w, ownerId), Map.of("role", "SALES_REP")), 403, "OWNER_ONLY");
    expect(
        send(admin, post(INVITATIONS, w), Map.of("email", uniqueEmail(), "role", "OWNER")),
        403,
        "OWNER_ONLY");
    expect(
        send(owner, post(INVITATIONS, w), Map.of("email", adminEmail, "role", "SALES_REP")),
        409,
        "ALREADY_MEMBER");

    String invited = uniqueEmail();
    JsonNode invitation =
        body(
            send(owner, post(INVITATIONS, w), Map.of("email", invited, "role", "SALES_REP"))
                .andExpect(status().isCreated()));
    expect(
        send(owner, post(INVITATIONS, w), Map.of("email", invited, "role", "SALES_REP")),
        409,
        "ALREADY_INVITED");
    String link = invitation.get("token").asText();
    expect(
        send(tokenFor(uniqueEmail()), post("/api/v1/invitations/{token}/accept", link)),
        403,
        "INVITATION_EMAIL_MISMATCH");
    long id = invitation.get("id").asLong();
    send(owner, delete(INVITATIONS + "/{id}", w, id)).andExpect(status().isNoContent());
    expect(send(owner, delete(INVITATIONS + "/{id}", w, id)), 409, "INVITATION_NOT_PENDING");
  }

  @Test
  void settingsRules() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");

    expect(
        send(owner, post(STAGES, w), Map.of("name", "won", "probability", 10)),
        409,
        "DUPLICATE_NAME");
    expect(send(owner, post(SOURCES, w), Map.of("name", "WEBSITE")), 409, "DUPLICATE_NAME");

    expect(
        send(owner, delete(STAGES + "/{id}", w, stageId(owner, w, "Won"))), 409, "STAGE_PROTECTED");
    long deal =
        body(send(owner, post(W + "/deals", w), Map.of("name", "D", "amount", "1"))
                .andExpect(status().isCreated()))
            .get("stage")
            .get("id")
            .asLong();
    expect(send(owner, delete(STAGES + "/{id}", w, deal)), 409, "STAGE_IN_USE");

    long website = sourceId(owner, w, "Website");
    send(owner, post(LEADS, w), Map.of("firstName", "Ada", "sourceId", website))
        .andExpect(status().isCreated());
    expect(send(owner, delete(SOURCES + "/{id}", w, website)), 409, "SOURCE_IN_USE");

    // The last open stage: the others go first.
    String other = tokenFor(uniqueEmail());
    long w2 = createWorkspace(other, "Other");
    List<Long> open = openStageIds(other, w2);
    for (long id : open.subList(1, open.size())) {
      send(other, delete(STAGES + "/{id}", w2, id)).andExpect(status().isNoContent());
    }
    expect(send(other, delete(STAGES + "/{id}", w2, open.get(0))), 409, "STAGE_PROTECTED");
  }

  /** A duplicate name that got past the service's check (a concurrent create) has the same code. */
  @Test
  void duplicateNameRaceHasItsCode() throws Exception {
    String email = uniqueEmail();
    String owner = tokenFor(email);
    long w = createWorkspace(owner, "Acme");
    Membership m = new Membership(w, userId(email), email, WorkspaceRole.OWNER);

    DataIntegrityViolationException race =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () -> WorkspaceContext.runAs(m, () -> sources.saveAndFlush(new LeadSource("website"))));

    var answer = handler.integrity(race, new MockHttpServletRequest("POST", "/x"));
    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(json.writeValueAsString(answer.getBody())).contains("\"code\":\"DUPLICATE_NAME\"");
  }

  /**
   * A delete that got past the service's in-use check (a deal or lead written at the same moment)
   * meets the foreign key, and answers with the same code as the check.
   */
  @Test
  void inUseRaceHasItsCode() throws Exception {
    String email = uniqueEmail();
    String owner = tokenFor(email);
    long w = createWorkspace(owner, "Acme");
    Membership m = new Membership(w, userId(email), email, WorkspaceRole.OWNER);
    long stage = openStageIds(owner, w).get(0);
    long source = sourceId(owner, w, "Website");
    send(owner, post(W + "/deals", w), Map.of("name", "D", "amount", "1", "stageId", stage))
        .andExpect(status().isCreated());
    send(owner, post(LEADS, w), Map.of("firstName", "Ada", "sourceId", source))
        .andExpect(status().isCreated());

    Map<String, Runnable> deletes =
        Map.of(
            "STAGE_IN_USE", () -> stages.deleteAllByIdInBatch(List.of(stage)),
            "SOURCE_IN_USE", () -> sources.deleteAllByIdInBatch(List.of(source)));
    for (var delete : deletes.entrySet()) {
      DataIntegrityViolationException race =
          catchThrowableOfType(
              DataIntegrityViolationException.class,
              () ->
                  WorkspaceContext.runAs(
                      m,
                      () -> {
                        delete.getValue().run();
                        return null;
                      }));

      var answer = handler.integrity(race, new MockHttpServletRequest("DELETE", "/x"));
      assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(answer.getBody().code()).hasToString(delete.getKey());
    }
  }

  @Test
  void recordRules() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");

    long fresh = id(send(owner, post(LEADS, w), Map.of("firstName", "Ada")));
    expect(
        send(owner, post(LEADS + "/{id}/convert", w, fresh), Map.of("createCompany", false)),
        409,
        "LEAD_NOT_QUALIFIED");
    send(owner, post(LEADS + "/{id}/status", w, fresh), Map.of("status", "QUALIFIED", "version", 0))
        .andExpect(status().isOk());
    send(owner, post(LEADS + "/{id}/convert", w, fresh), Map.of("createCompany", false))
        .andExpect(status().isOk());
    expect(
        send(owner, post(LEADS + "/{id}/status", w, fresh), Map.of("status", "NEW", "version", 2)),
        409,
        "LEAD_CONVERTED");
    Map<String, Object> edit = new HashMap<>();
    edit.put("firstName", "Ada");
    edit.put("version", 2);
    expect(send(owner, put(LEADS + "/{id}", w, fresh), edit), 409, "LEAD_CONVERTED");

    long task = id(send(owner, post(W + "/tasks", w), Map.of("title", "Call")));
    send(owner, post(W + "/tasks/{id}/complete", w, task), Map.of("version", 0))
        .andExpect(status().isOk());
    expect(
        send(owner, post(W + "/tasks/{id}/complete", w, task), Map.of("version", 1)),
        409,
        "TASK_STATUS_CONFLICT");
  }

  private static ResultActions expect(ResultActions result, int status, String code)
      throws Exception {
    return result
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.code").value(code));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
  }

  private long stageId(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No stage " + name);
  }

  private List<Long> openStageIds(String token, long w) throws Exception {
    List<Long> ids = new ArrayList<>();
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      if (s.get("kind").asText().equals("OPEN")) ids.add(s.get("id").asLong());
    }
    return ids;
  }

  private long sourceId(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(SOURCES, w)))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No source " + name);
  }
}
