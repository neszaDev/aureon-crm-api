package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Security review probe (phase 4): extra JSON fields in every create and update body (an id, the
 * workspace, the version on a create, timestamps, archive state, status, conversion and closing
 * fields, the creator or author, a role) change nothing, and a sales rep can't name another owner.
 */
class MassAssignmentProbeIT extends WorkspaceTestSupport {
  private static final String W = "/api/v1/workspaces/{workspaceId}";
  private static final long FOREIGN_ID = 987_654_321L;
  private static final String LONG_AGO = "2001-01-01T00:00:00Z";

  @Autowired JdbcTemplate jdbc;

  private String owner;
  private String ownerEmail;
  private long ownerId;
  private String rep;
  private long repId;
  private long w;
  private long otherWorkspace;
  private long wonStage;

  private void setUp() throws Exception {
    ownerEmail = uniqueEmail();
    owner = tokenFor(ownerEmail);
    ownerId = users.findByEmail(ownerEmail).orElseThrow().getId();
    w = createWorkspace(owner, "Mass assignment");
    String repEmail = uniqueEmail();
    rep = tokenFor(repEmail);
    repId = users.findByEmail(repEmail).orElseThrow().getId();
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    otherWorkspace = createWorkspace(tokenFor(uniqueEmail()), "Other");
    for (JsonNode s : body(send(owner, get(W + "/pipeline-stages", w)))) {
      if (s.get("kind").asText().equals("WON")) wonStage = s.get("id").asLong();
    }
  }

  /** Fields no request may set, whatever the record. */
  private Map<String, Object> extras() {
    Map<String, Object> e = new HashMap<>();
    e.put("id", FOREIGN_ID);
    e.put("workspaceId", otherWorkspace);
    e.put("createdAt", LONG_AGO);
    e.put("updatedAt", LONG_AGO);
    e.put("archivedAt", LONG_AGO);
    e.put("archived", true);
    e.put("createdBy", ownerId);
    e.put("authorId", ownerId);
    e.put("owner", Map.of("userId", ownerId));
    e.put("status", "CONVERTED");
    e.put("convertedAt", LONG_AGO);
    e.put("convertedCompanyId", 1);
    e.put("convertedContactId", 1);
    e.put("convertedDealId", 1);
    e.put("unqualifiedReason", "planted");
    e.put("closedAt", LONG_AGO);
    e.put("lostReason", "planted");
    e.put("completedAt", LONG_AGO);
    e.put("stage", Map.of("id", wonStage));
    e.put("leadId", FOREIGN_ID);
    e.put("role", "OWNER");
    e.put("kind", "WON");
    e.put("position", 0);
    return e;
  }

  private Map<String, Object> with(Map<String, Object> body, Object... more) {
    Map<String, Object> all = extras();
    all.putAll(body);
    for (int i = 0; i < more.length; i += 2) all.put((String) more[i], more[i + 1]);
    return all;
  }

  @Test
  void extraFieldsOnCreatesAndUpdatesChangeNothing() throws Exception {
    setUp();
    Instant start = Instant.now().minusSeconds(5);
    List<String> wrong = new ArrayList<>();

    Map<String, Map<String, Object>> creates = new HashMap<>();
    creates.put("companies", Map.of("name", "Co"));
    creates.put("contacts", Map.of("firstName", "Ada", "lastName", "L", "fullName", "planted"));
    creates.put("leads", Map.of("firstName", "Grace"));
    creates.put("deals", Map.of("name", "Deal", "amount", "10"));
    creates.put("tasks", Map.of("title", "Task", "assignee", Map.of("userId", ownerId)));
    // An activity needs a link: the rep's own company, created plainly first.
    long company = id(send(rep, post(W + "/companies", w), Map.of("name", "Link")));
    creates.put(
        "activities",
        Map.of("type", "NOTE", "subject", "Note", "companyId", company, "author", Map.of()));

    for (var c : creates.entrySet()) {
      // Without the extras' leadId, which only a conversion sets (a task or activity link is 400).
      Map<String, Object> body = with(c.getValue());
      if (!c.getKey().equals("deals")) body.remove("leadId");
      MockHttpServletResponse res =
          send(rep, post(W + "/" + c.getKey(), w), body).andReturn().getResponse();
      if (res.getStatus() != 201) {
        wrong.add("POST " + c.getKey() + " -> " + res.getStatus() + " " + res.getContentAsString());
        continue;
      }
      JsonNode r = json.readTree(res.getContentAsString());
      check(wrong, "POST " + c.getKey(), c.getKey(), r, start);

      // The update: the rep's own record, its version and owner, plus the extras again.
      Map<String, Object> update = new HashMap<>(c.getValue());
      update.put("version", r.get("version").asLong());
      switch (c.getKey()) {
        case "tasks" -> update.put("assigneeId", repId);
        case "activities" -> {}
        default -> update.put("ownerId", repId);
      }
      Map<String, Object> updateBody = with(update, "stageId", wonStage);
      if (!c.getKey().equals("deals")) updateBody.remove("leadId");
      MockHttpServletResponse up =
          send(rep, put(W + "/" + c.getKey() + "/{id}", w, r.get("id").asLong()), updateBody)
              .andReturn()
              .getResponse();
      if (up.getStatus() != 200) {
        wrong.add("PUT " + c.getKey() + " -> " + up.getStatus() + " " + up.getContentAsString());
        continue;
      }
      check(wrong, "PUT " + c.getKey(), c.getKey(), json.readTree(up.getContentAsString()), start);
    }

    // Settings: a new stage is OPEN at the end, a new source active.
    JsonNode stage =
        body(
            send(
                    owner,
                    post(W + "/pipeline-stages", w),
                    with(Map.of("name", "S", "probability", 5)))
                .andExpect(status().isCreated()));
    if (!stage.get("kind").asText().equals("OPEN") || stage.get("position").asInt() == 0) {
      wrong.add("POST pipeline-stages took kind/position: " + stage);
    }
    JsonNode source =
        body(
            send(owner, post(W + "/lead-sources", w), with(Map.of("name", "Src")))
                .andExpect(status().isCreated()));
    if (source.get("archived").asBoolean() || source.get("id").asLong() == FOREIGN_ID) {
      wrong.add("POST lead-sources took archived/id: " + source);
    }

    // Workspace: the creator owns the new one and nobody else is in it; an update keeps the role.
    JsonNode created =
        body(
            send(
                    rep,
                    post("/api/v1/workspaces"),
                    with(
                        Map.of(
                            "name", "Mine", "defaultCurrency", "EUR", "timezone", "Europe/Paris"),
                        "ownerId",
                        ownerId,
                        "role",
                        "SALES_REP"))
                .andExpect(status().isCreated()));
    long mine = created.get("id").asLong();
    if (mine == FOREIGN_ID || !created.get("role").asText().equals("OWNER")) {
      wrong.add("POST workspaces: " + created);
    }
    if (members.existsByWorkspaceIdAndUserId(mine, ownerId)) {
      wrong.add("POST workspaces made the named ownerId a member");
    }
    JsonNode updated =
        body(
            send(
                    owner,
                    put(W, w),
                    with(
                        Map.of(
                            "name", "Renamed",
                            "defaultCurrency", "EUR",
                            "timezone", "Europe/Paris",
                            "version", 0),
                        "permissions",
                        List.of(),
                        "role",
                        "SALES_REP"))
                .andExpect(status().isOk()));
    if (updated.get("id").asLong() != w || !updated.get("role").asText().equals("OWNER")) {
      wrong.add("PUT workspace: " + updated);
    }

    // Invitations: the workspace and token are the server's; registering takes the invited email
    // and role whatever the body says.
    String invited = uniqueEmail();
    JsonNode invitation =
        body(
            send(
                    owner,
                    post(W + "/invitations", w),
                    with(Map.of("email", invited, "role", "SALES_REP"), "token", "chosen-token"))
                .andExpect(status().isCreated()));
    if (invitation.get("token").asText().equals("chosen-token")) {
      wrong.add("POST invitations took the token");
    }
    if (jdbc.queryForObject(
            "SELECT workspace_id FROM workspace_invitations WHERE id = ?",
            Long.class,
            invitation.get("id").asLong())
        != w) {
      wrong.add("POST invitations took the workspace");
    }
    String other = uniqueEmail();
    JsonNode registered =
        body(
            mvc.perform(
                    post("/api/v1/invitations/{t}/register", invitation.get("token").asText())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                with(Map.of("password", PASSWORD), "email", other))))
                .andExpect(status().isCreated()));
    JsonNode me = body(send(registered.get("accessToken").asText(), get("/api/v1/users/me")));
    if (!me.get("email").asText().equals(invited) || !me.get("role").asText().equals("USER")) {
      wrong.add("invitation register took email/role: " + me);
    }
    if (membership(w, invited).role() != WorkspaceRole.SALES_REP) {
      wrong.add("invitation register took the role: " + membership(w, invited).role());
    }

    // A member update changes the role of the member in the path, nobody else's.
    send(
            owner,
            put(W + "/members/{userId}", w, repId),
            Map.of("role", "MANAGER", "userId", ownerId, "workspaceId", otherWorkspace))
        .andExpect(status().isOk());
    if (membership(w, ownerEmail).role() != WorkspaceRole.OWNER) {
      wrong.add("PUT members changed the owner");
    }

    // Accounts: registration and the profile never take a role, an email or a token version.
    String email = uniqueEmail();
    String token =
        body(mvc.perform(
                    post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                with(
                                    Map.of("email", email, "password", PASSWORD),
                                    "role",
                                    "ADMIN",
                                    "tokenVersion",
                                    7))))
                .andExpect(status().isCreated()))
            .get("accessToken")
            .asText();
    JsonNode profile =
        body(
            send(
                    token,
                    put("/api/v1/users/me"),
                    with(Map.of("displayName", "Me"), "role", "ADMIN", "email", uniqueEmail()))
                .andExpect(status().isOk()));
    if (!profile.get("role").asText().equals("USER")
        || !profile.get("email").asText().equals(email)) {
      wrong.add("account took role/email: " + profile);
    }
    send(token, get("/api/v1/users")).andExpect(status().isForbidden());

    assertThat(wrong).as("extra fields that took effect").isEmpty();
  }

  /** What any create or update response must show despite the extras. */
  private void check(List<String> wrong, String what, String kind, JsonNode r, Instant start)
      throws Exception {
    long id = r.get("id").asLong();
    List<String> bad = new ArrayList<>();
    if (id == FOREIGN_ID) bad.add("id");
    if (what.startsWith("POST") && r.get("version").asLong() != 0) bad.add("version");
    if (r.has("archived") && r.get("archived").asBoolean()) bad.add("archived");
    if (r.has("archivedAt")) bad.add("archivedAt");
    if (Instant.parse(r.get("createdAt").asText()).isBefore(start)) bad.add("createdAt");
    Long workspace =
        jdbc.queryForObject("SELECT workspace_id FROM " + kind + " WHERE id = ?", Long.class, id);
    if (workspace == null || workspace != w) bad.add("workspaceId");
    switch (kind) {
      case "tasks" -> {
        if (r.path("assignee").path("userId").asLong() != repId) bad.add("assignee");
        if (r.path("createdBy").path("userId").asLong() != repId) bad.add("createdBy");
        if (!r.get("status").asText().equals("OPEN") || r.has("completedAt")) bad.add("status");
      }
      case "activities" -> {
        if (r.path("author").path("userId").asLong() != repId) bad.add("author");
      }
      default -> {
        if (r.path("owner").path("userId").asLong() != repId) bad.add("owner");
      }
    }
    if (kind.equals("leads")
        && (!r.get("status").asText().equals("NEW")
            || r.has("convertedAt")
            || r.has("convertedCompany")
            || r.has("convertedContact")
            || r.has("convertedDeal")
            || r.has("unqualifiedReason"))) bad.add("status/converted*");
    if (kind.equals("deals")
        && (!r.get("status").asText().equals("OPEN")
            || r.path("stage").path("id").asLong() == wonStage
            || r.has("closedAt")
            || r.has("lostReason")
            || r.has("lead"))) bad.add("status/stage/closedAt/lostReason/lead");
    if (kind.equals("contacts") && !r.get("fullName").asText().equals("Ada L")) bad.add("fullName");
    if (!bad.isEmpty()) wrong.add(what + " took " + bad + ": " + r);
  }

  @Test
  void aSalesRepCannotNameAnotherOwnerOrAssignee() throws Exception {
    setUp();
    List<String> wrong = new ArrayList<>();
    record Row(String label, MockHttpServletRequestBuilder request, Map<String, Object> body) {}
    long own = id(send(rep, post(W + "/companies", w), Map.of("name", "Own")));
    Map<String, Object> unassign = new HashMap<>();
    unassign.put("name", "Own");
    unassign.put("ownerId", null);
    unassign.put("version", 0);
    List<Row> rows =
        List.of(
            new Row(
                "POST companies",
                post(W + "/companies", w),
                Map.of("name", "X", "ownerId", ownerId)),
            new Row(
                "POST contacts",
                post(W + "/contacts", w),
                Map.of("firstName", "X", "ownerId", ownerId)),
            new Row(
                "POST leads", post(W + "/leads", w), Map.of("firstName", "X", "ownerId", ownerId)),
            new Row(
                "POST deals",
                post(W + "/deals", w),
                Map.of("name", "X", "amount", "1", "ownerId", ownerId)),
            new Row(
                "POST tasks", post(W + "/tasks", w), Map.of("title", "X", "assigneeId", ownerId)),
            new Row(
                "PUT own company to the owner",
                put(W + "/companies/{id}", w, own),
                Map.of("name", "Own", "ownerId", ownerId, "version", 0)),
            new Row("PUT own company unassigned", put(W + "/companies/{id}", w, own), unassign));
    for (Row row : rows) {
      MockHttpServletResponse res = send(rep, row.request(), row.body()).andReturn().getResponse();
      if (res.getStatus() != 403) {
        wrong.add(row.label() + " -> " + res.getStatus() + " " + res.getContentAsString());
      }
    }
    assertThat(wrong).isEmpty();
    // Nothing was created for the owner, and the rep's company is still the rep's.
    assertThat(
            body(send(owner, get(W + "/companies?ownerId=" + ownerId, w)))
                .get("totalElements")
                .asLong())
        .isZero();
    assertThat(body(send(rep, get(W + "/companies/{id}", w, own))).get("version").asLong())
        .isZero();
  }

  private long id(org.springframework.test.web.servlet.ResultActions result) throws Exception {
    return body(result.andExpect(status().isCreated())).get("id").asLong();
  }
}
