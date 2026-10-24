package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Security review probe (phase 4): the permission matrix (docs/crm/permissions.md) from the side of
 * the roles that lack each permission, the owner rules an admin can't break, self-promotion, a
 * demoted or removed member's existing token, and the system admin's endpoints and workspaces.
 * Every refused request writes nothing: no audit event by the refused members.
 */
class PrivilegeEscalationProbeIT extends WorkspaceTestSupport {
  private static final String W = "/api/v1/workspaces/{workspaceId}";

  @Autowired JdbcTemplate jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  record Row(
      String who,
      String token,
      String label,
      MockHttpServletRequestBuilder request,
      Object body,
      String code) {}

  @Test
  void rolesWithoutAPermissionAreRefusedAndChangeNothing() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Matrix");
    long ownerId = userId(ownerEmail);
    String admin = member(w, WorkspaceRole.ADMIN);
    long adminId = lastUserId;
    String manager = member(w, WorkspaceRole.MANAGER);
    long managerId = lastUserId;
    String rep = member(w, WorkspaceRole.SALES_REP);
    long repId = lastUserId;

    // The owner's records, and one of each setting.
    long company = create(owner, w, "companies", Map.of("name", "Owner's"));
    long contact = create(owner, w, "contacts", Map.of("firstName", "Owner's"));
    long lead = create(owner, w, "leads", Map.of("firstName", "Owner's"));
    send(
            owner,
            post(W + "/leads/{id}/status", w, lead),
            Map.of("status", "QUALIFIED", "version", 0))
        .andExpect(status().isOk());
    long deal = create(owner, w, "deals", Map.of("name", "Owner's", "amount", "1"));
    long task = create(owner, w, "tasks", Map.of("title", "Owner's"));
    long activity =
        create(
            owner,
            w,
            "activities",
            Map.of("type", "NOTE", "subject", "Owner's", "companyId", company));
    long stage = create(owner, w, "pipeline-stages", Map.of("name", "Extra", "probability", 5));
    long source = create(owner, w, "lead-sources", Map.of("name", "Extra"));
    long invitation =
        create(owner, w, "invitations", Map.of("email", uniqueEmail(), "role", "SALES_REP"));
    long ownerInvitation =
        create(owner, w, "invitations", Map.of("email", uniqueEmail(), "role", "OWNER"));
    List<Long> openStages = new ArrayList<>();
    for (JsonNode s : body(send(owner, get(W + "/pipeline-stages", w)))) {
      if (s.get("kind").asText().equals("OPEN")) openStages.add(s.get("id").asLong());
    }
    long leadVersion = 1; // after QUALIFIED

    List<Row> rows = new ArrayList<>();
    // WORKSPACE_MANAGE, SETTINGS_MANAGE, MEMBERS_MANAGE, AUDIT_READ: neither a manager nor a rep.
    for (String who : List.of("manager", "rep")) {
      String t = who.equals("manager") ? manager : rep;
      long target = who.equals("manager") ? repId : managerId;
      rows.add(
          row(
              who,
              t,
              "PUT workspace",
              put(W, w),
              Map.of(
                  "name",
                  "X",
                  "defaultCurrency",
                  "EUR",
                  "timezone",
                  "Europe/Paris",
                  "version",
                  0)));
      rows.add(
          row(
              who,
              t,
              "POST stage",
              post(W + "/pipeline-stages", w),
              Map.of("name", "X", "probability", 1)));
      rows.add(
          row(
              who,
              t,
              "PUT stage",
              put(W + "/pipeline-stages/{id}", w, stage),
              Map.of("name", "X", "probability", 1, "version", 0)));
      rows.add(
          row(
              who,
              t,
              "PUT stage order",
              put(W + "/pipeline-stages/order", w),
              Map.of("stageIds", openStages.reversed())));
      rows.add(row(who, t, "DELETE stage", delete(W + "/pipeline-stages/{id}", w, stage), null));
      rows.add(row(who, t, "POST source", post(W + "/lead-sources", w), Map.of("name", "X")));
      rows.add(
          row(
              who,
              t,
              "PUT source",
              put(W + "/lead-sources/{id}", w, source),
              Map.of("name", "X", "archived", true, "version", 0)));
      rows.add(row(who, t, "DELETE source", delete(W + "/lead-sources/{id}", w, source), null));
      rows.add(row(who, t, "GET invitations", get(W + "/invitations", w), null));
      rows.add(
          row(
              who,
              t,
              "POST invitation",
              post(W + "/invitations", w),
              Map.of("email", uniqueEmail(), "role", "SALES_REP")));
      rows.add(
          row(who, t, "DELETE invitation", delete(W + "/invitations/{id}", w, invitation), null));
      rows.add(
          row(who, t, "PUT member", put(W + "/members/{u}", w, target), Map.of("role", "ADMIN")));
      rows.add(row(who, t, "DELETE member", delete(W + "/members/{u}", w, target), null));
      rows.add(row(who, t, "GET audit trail", get(W + "/audit-events", w), null));
      rows.add(
          row(who, t, "GET audit by actor", get(W + "/audit-events?actorId=" + ownerId, w), null));
      rows.add(
          row(
              who,
              t,
              "GET audit of a member",
              get(W + "/audit-events?entityType=MEMBER&entityId=" + ownerId, w),
              null));
      rows.add(
          row(
              who,
              t,
              "GET audit of the workspace",
              get(W + "/audit-events?entityType=WORKSPACE&entityId=" + w, w),
              null));
      rows.add(
          row(
              who,
              t,
              "GET audit of an invitation",
              get(W + "/audit-events?entityType=INVITATION&entityId=" + invitation, w),
              null));
      // Self-promotion.
      rows.add(
          row(
              who,
              t,
              "PUT own role ADMIN",
              put(W + "/members/{u}", w, who.equals("manager") ? managerId : repId),
              Map.of("role", "ADMIN")));
      rows.add(
          row(
              who,
              t,
              "PUT own role OWNER",
              put(W + "/members/{u}", w, who.equals("manager") ? managerId : repId),
              Map.of("role", "OWNER")));
    }
    // EXPORT, RECORDS_EDIT_ANY, RECORDS_ASSIGN: a rep on the owner's records.
    for (String list : List.of("companies", "contacts", "leads", "deals")) {
      rows.add(
          row("rep", rep, "GET " + list + " export", get(W + "/" + list + "/export", w), null));
    }
    rows.add(
        row(
            "rep",
            rep,
            "PUT company",
            put(W + "/companies/{id}", w, company),
            Map.of("name", "X", "ownerId", ownerId, "version", 0)));
    rows.add(
        row(
            "rep",
            rep,
            "PUT contact",
            put(W + "/contacts/{id}", w, contact),
            Map.of("firstName", "X", "ownerId", ownerId, "version", 0)));
    rows.add(
        row(
            "rep",
            rep,
            "PUT lead",
            put(W + "/leads/{id}", w, lead),
            Map.of("firstName", "X", "ownerId", ownerId, "version", leadVersion)));
    rows.add(
        row(
            "rep",
            rep,
            "PUT deal",
            put(W + "/deals/{id}", w, deal),
            Map.of("name", "X", "amount", "1", "ownerId", ownerId, "version", 0)));
    rows.add(
        row(
            "rep",
            rep,
            "PUT task",
            put(W + "/tasks/{id}", w, task),
            Map.of("title", "X", "assigneeId", ownerId, "version", 0)));
    rows.add(
        row(
            "rep",
            rep,
            "PUT activity",
            put(W + "/activities/{id}", w, activity),
            Map.of("type", "NOTE", "subject", "X", "companyId", company, "version", 0)));
    rows.add(row("rep", rep, "DELETE activity", delete(W + "/activities/{id}", w, activity), null));
    for (var r :
        Map.of("companies", company, "contacts", contact, "leads", lead, "deals", deal)
            .entrySet()) {
      rows.add(
          row(
              "rep",
              rep,
              "archive " + r.getKey(),
              post(W + "/" + r.getKey() + "/{id}/archive", w, r.getValue()),
              null));
      rows.add(
          row(
              "rep",
              rep,
              "restore " + r.getKey(),
              post(W + "/" + r.getKey() + "/{id}/restore", w, r.getValue()),
              null));
    }
    rows.add(
        row(
            "rep",
            rep,
            "lead status",
            post(W + "/leads/{id}/status", w, lead),
            Map.of("status", "CONTACTED", "version", leadVersion)));
    rows.add(
        row(
            "rep",
            rep,
            "lead convert",
            post(W + "/leads/{id}/convert", w, lead),
            Map.of("createCompany", false)));
    rows.add(
        row(
            "rep",
            rep,
            "deal move",
            post(W + "/deals/{id}/move", w, deal),
            Map.of("stageId", openStages.get(1), "version", 0)));
    for (String action : List.of("complete", "reopen", "cancel")) {
      rows.add(
          row(
              "rep",
              rep,
              "task " + action,
              post(W + "/tasks/{id}/" + action, w, task),
              Map.of("version", 0)));
    }
    // Owner rules: an admin manages members, but not owners, and can't make anyone an owner.
    rows.add(
        row(
            "admin",
            admin,
            "PUT owner's role",
            put(W + "/members/{u}", w, ownerId),
            Map.of("role", "ADMIN"),
            "OWNER_ONLY"));
    rows.add(
        row(
            "admin",
            admin,
            "DELETE owner",
            delete(W + "/members/{u}", w, ownerId),
            null,
            "OWNER_ONLY"));
    rows.add(
        row(
            "admin",
            admin,
            "PUT rep to OWNER",
            put(W + "/members/{u}", w, repId),
            Map.of("role", "OWNER"),
            "OWNER_ONLY"));
    rows.add(
        row(
            "admin",
            admin,
            "PUT own role OWNER",
            put(W + "/members/{u}", w, adminId),
            Map.of("role", "OWNER"),
            "OWNER_ONLY"));
    rows.add(
        row(
            "admin",
            admin,
            "POST OWNER invitation",
            post(W + "/invitations", w),
            Map.of("email", uniqueEmail(), "role", "OWNER"),
            "OWNER_ONLY"));
    rows.add(
        row(
            "admin",
            admin,
            "DELETE OWNER invitation",
            delete(W + "/invitations/{id}", w, ownerInvitation),
            null,
            "OWNER_ONLY"));

    List<String> wrong = new ArrayList<>();
    for (Row r : rows) {
      MockHttpServletResponse res =
          send(r.token(), r.request(), r.body()).andReturn().getResponse();
      String type = res.getContentType();
      String code =
          type != null && type.startsWith("application/json")
              ? json.readTree(res.getContentAsString()).path("code").asText()
              : "";
      if (res.getStatus() != 403 || !code.equals(r.code())) {
        wrong.add(
            r.who() + " " + r.label() + " -> " + res.getStatus() + " " + res.getContentAsString());
      }
    }
    assertThat(wrong).as("refusals that were not a 403 with the expected code").isEmpty();
    assertThat(rows).hasSizeGreaterThanOrEqualTo(71);

    // Nothing written by any of them, and the owner's records are as they were.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE workspace_id = ? AND actor_id IN (?, ?, ?)",
                Long.class,
                w,
                adminId,
                managerId,
                repId))
        .isZero();
    for (var r :
        Map.of(
                "companies",
                company,
                "contacts",
                contact,
                "deals",
                deal,
                "tasks",
                task,
                "activities",
                activity)
            .entrySet()) {
      assertThat(
              body(send(owner, get(W + "/" + r.getKey() + "/{id}", w, r.getValue())))
                  .get("version")
                  .asLong())
          .as(r.getKey())
          .isZero();
    }
    assertThat(body(send(owner, get(W + "/leads/{id}", w, lead))).get("status").asText())
        .isEqualTo("QUALIFIED");
    assertThat(membership(w, ownerEmail).role()).isEqualTo(WorkspaceRole.OWNER);
    assertThat(body(send(owner, get(W + "/invitations", w)))).hasSize(2);
  }

  @Test
  void aDemotedOrRemovedMembersTokenLosesTheRightsAtOnce() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Demotion");
    String admin = member(w, WorkspaceRole.ADMIN);
    long adminId = lastUserId;
    send(admin, get(W + "/invitations", w)).andExpect(status().isOk());

    send(owner, put(W + "/members/{u}", w, adminId), Map.of("role", "SALES_REP"))
        .andExpect(status().isOk());
    // The same access token, issued while an admin.
    send(admin, get(W + "/invitations", w)).andExpect(status().isForbidden());
    send(admin, get(W + "/audit-events", w)).andExpect(status().isForbidden());
    send(admin, post(W + "/pipeline-stages", w), Map.of("name", "X", "probability", 1))
        .andExpect(status().isForbidden());
    send(admin, put(W + "/members/{u}", w, adminId), Map.of("role", "ADMIN"))
        .andExpect(status().isForbidden());

    send(owner, delete(W + "/members/{u}", w, adminId)).andExpect(status().isNoContent());
    send(admin, get(W, w)).andExpect(status().isNotFound());
    send(admin, get(W + "/companies", w)).andExpect(status().isNotFound());
    assertThat(body(send(admin, get("/api/v1/workspaces")))).isEmpty();
  }

  /** A system ADMIN manages accounts, not workspaces: outside its own, every endpoint is a 404. */
  @Test
  void theSystemAdminHasNoWayIntoAWorkspace() throws Exception {
    long w = createWorkspace(tokenFor(uniqueEmail()), "Private");
    String admin = adminAccessToken();
    List<String> wrong = new ArrayList<>();
    for (var info : mappings.getHandlerMethods().keySet()) {
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (!pattern.startsWith(W)) continue;
        for (RequestMethod m : info.getMethodsCondition().getMethods()) {
          String path =
              pattern.replace("{workspaceId}", String.valueOf(w)).replaceAll("\\{[^/]+}", "1");
          boolean hasBody = m == RequestMethod.POST || m == RequestMethod.PUT;
          var res =
              send(admin, request(HttpMethod.valueOf(m.name()), path), hasBody ? Map.of() : null)
                  .andReturn()
                  .getResponse();
          if (res.getStatus() != 404) wrong.add(m + " " + pattern + " -> " + res.getStatus());
        }
      }
    }
    assertThat(wrong).isEmpty();
    assertThat(body(send(admin, get("/api/v1/workspaces"))))
        .noneMatch(n -> n.get("id").asLong() == w);
  }

  /** Every account-management endpoint (all of /users but /me) refuses an ordinary account. */
  @Test
  void accountManagementIsForSystemAdminsOnly() throws Exception {
    String email = uniqueEmail();
    String user = tokenFor(email);
    long self = userId(email);
    List<String> wrong = new ArrayList<>();
    int seen = 0;
    for (var info : mappings.getHandlerMethods().keySet()) {
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (!pattern.startsWith("/api/v1/users") || pattern.startsWith("/api/v1/users/me"))
          continue;
        for (RequestMethod m : info.getMethodsCondition().getMethods()) {
          seen++;
          String path = pattern.replaceAll("\\{[^/]+}", String.valueOf(self));
          Object body = m == RequestMethod.PATCH ? Map.of("role", "ADMIN") : null;
          var res =
              send(user, request(HttpMethod.valueOf(m.name()), path), body)
                  .andReturn()
                  .getResponse();
          String code = json.readTree(res.getContentAsString()).path("code").asText();
          if (res.getStatus() != 403 || !code.equals("FORBIDDEN")) {
            wrong.add(
                m + " " + pattern + " -> " + res.getStatus() + " " + res.getContentAsString());
          }
        }
      }
    }
    assertThat(seen).isGreaterThanOrEqualTo(4);
    assertThat(wrong).isEmpty();
    assertThat(body(send(user, get("/api/v1/users/me"))).get("role").asText()).isEqualTo("USER");
  }

  private long lastUserId;

  private String member(long w, WorkspaceRole role) throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    addMember(w, email, role);
    lastUserId = userId(email);
    return token;
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private static Row row(
      String who, String token, String label, MockHttpServletRequestBuilder r, Object body) {
    return new Row(who, token, label, r, body, "FORBIDDEN");
  }

  private static Row row(
      String who,
      String token,
      String label,
      MockHttpServletRequestBuilder r,
      Object body,
      String code) {
    return new Row(who, token, label, r, body, code);
  }

  private long create(String token, long w, String collection, Map<String, Object> body)
      throws Exception {
    return body(send(token, post(W + "/" + collection, w), body).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }
}
