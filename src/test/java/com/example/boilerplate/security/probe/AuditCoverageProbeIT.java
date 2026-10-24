package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Security review probe (phase 4): every change through a workspace or invitation endpoint, and
 * every export, writes an audit event naming who did it, in the same request. The endpoints are
 * taken from the running application: a new one that changes something must be added here (and
 * audited) or this fails.
 */
class AuditCoverageProbeIT extends WorkspaceTestSupport {
  private static final String W = "/api/v1/workspaces/{workspaceId}";

  @Autowired JdbcTemplate jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  private final Set<String> covered = new TreeSet<>();
  private final List<String> wrong = new ArrayList<>();

  @Test
  void everyChangeAndExportIsAudited() throws Exception {
    String ownerEmail = uniqueEmail();
    String o = tokenFor(ownerEmail);
    long me = userId(ownerEmail);

    long w =
        audited(
                "POST /api/v1/workspaces",
                o,
                me,
                post("/api/v1/workspaces"),
                Map.of("name", "Audited", "defaultCurrency", "EUR", "timezone", "Europe/Paris"),
                "CREATE",
                "WORKSPACE")
            .get("id")
            .asLong();
    audited(
        "PUT " + W,
        o,
        me,
        put(W, w),
        Map.of(
            "name",
            "Audited 2",
            "defaultCurrency",
            "EUR",
            "timezone",
            "Europe/Paris",
            "version",
            0),
        "UPDATE",
        "WORKSPACE");

    // Settings.
    long stage =
        audited(
                "POST " + W + "/pipeline-stages",
                o,
                me,
                post(W + "/pipeline-stages", w),
                Map.of("name", "Extra", "probability", 10),
                "CREATE",
                "PIPELINE_STAGE")
            .get("id")
            .asLong();
    audited(
        "PUT " + W + "/pipeline-stages/{id}",
        o,
        me,
        put(W + "/pipeline-stages/{id}", w, stage),
        Map.of("name", "Extra 2", "probability", 20, "version", 0),
        "UPDATE",
        "PIPELINE_STAGE");
    List<Long> open = new ArrayList<>();
    for (JsonNode s : body(send(o, get(W + "/pipeline-stages", w)))) {
      if (s.get("kind").asText().equals("OPEN")) open.add(s.get("id").asLong());
    }
    audited(
        "PUT " + W + "/pipeline-stages/order",
        o,
        me,
        put(W + "/pipeline-stages/order", w),
        Map.of("stageIds", open.reversed()),
        "UPDATE",
        "PIPELINE_STAGE");
    long source =
        audited(
                "POST " + W + "/lead-sources",
                o,
                me,
                post(W + "/lead-sources", w),
                Map.of("name", "Extra"),
                "CREATE",
                "LEAD_SOURCE")
            .get("id")
            .asLong();
    audited(
        "PUT " + W + "/lead-sources/{id}",
        o,
        me,
        put(W + "/lead-sources/{id}", w, source),
        Map.of("name", "Extra 2", "archived", false, "version", 0),
        "UPDATE",
        "LEAD_SOURCE");

    // Records: create, edit, archive, restore, and each one's actions.
    Map<String, String> types =
        Map.of("companies", "COMPANY", "contacts", "CONTACT", "leads", "LEAD", "deals", "DEAL");
    Map<String, Map<String, Object>> creates =
        Map.of(
            "companies", Map.of("name", "Co"),
            "contacts", Map.of("firstName", "Ada"),
            "leads", Map.of("firstName", "Grace"),
            "deals", Map.of("name", "Deal", "amount", "10"));
    Map<String, Map<String, Object>> edits =
        Map.of(
            "companies", Map.of("name", "Co 2", "ownerId", me, "version", 0),
            "contacts", Map.of("firstName", "Ada 2", "ownerId", me, "version", 0),
            "leads", Map.of("firstName", "Grace 2", "ownerId", me, "version", 0),
            "deals", Map.of("name", "Deal 2", "amount", "10", "ownerId", me, "version", 0));
    Map<String, Long> ids = new java.util.HashMap<>();
    for (String c : List.of("companies", "contacts", "leads", "deals")) {
      String type = types.get(c);
      long id =
          audited(
                  "POST " + W + "/" + c,
                  o,
                  me,
                  post(W + "/" + c, w),
                  creates.get(c),
                  "CREATE",
                  type)
              .get("id")
              .asLong();
      ids.put(c, id);
      audited(
          "PUT " + W + "/" + c + "/{id}",
          o,
          me,
          put(W + "/" + c + "/{id}", w, id),
          edits.get(c),
          "UPDATE",
          type);
      audited(
          "POST " + W + "/" + c + "/{id}/archive",
          o,
          me,
          post(W + "/" + c + "/{id}/archive", w, id),
          null,
          "ARCHIVE",
          type);
      audited(
          "POST " + W + "/" + c + "/{id}/restore",
          o,
          me,
          post(W + "/" + c + "/{id}/restore", w, id),
          null,
          "RESTORE",
          type);
    }
    long lead = ids.get("leads");
    audited(
        "POST " + W + "/leads/{id}/status",
        o,
        me,
        post(W + "/leads/{id}/status", w, lead),
        Map.of("status", "QUALIFIED", "version", version(o, W + "/leads/{id}", w, lead)),
        "STATUS_CHANGE",
        "LEAD");
    audited(
        "POST " + W + "/leads/{id}/convert",
        o,
        me,
        post(W + "/leads/{id}/convert", w, lead),
        Map.of("createCompany", false),
        "CONVERT",
        "LEAD");
    long deal = ids.get("deals");
    long target = open.stream().filter(s -> s != stage).findFirst().orElseThrow();
    long dealStage = body(send(o, get(W + "/deals/{id}", w, deal))).get("stage").get("id").asLong();
    if (dealStage == target)
      target = open.stream().filter(s -> s != stage && s != dealStage).findFirst().orElseThrow();
    audited(
        "POST " + W + "/deals/{id}/move",
        o,
        me,
        post(W + "/deals/{id}/move", w, deal),
        Map.of("stageId", target, "version", version(o, W + "/deals/{id}", w, deal)),
        "STAGE_CHANGE",
        "DEAL");

    long task =
        audited(
                "POST " + W + "/tasks",
                o,
                me,
                post(W + "/tasks", w),
                Map.of("title", "Task"),
                "CREATE",
                "TASK")
            .get("id")
            .asLong();
    audited(
        "PUT " + W + "/tasks/{id}",
        o,
        me,
        put(W + "/tasks/{id}", w, task),
        Map.of("title", "Task 2", "assigneeId", me, "version", 0),
        "UPDATE",
        "TASK");
    for (String action : List.of("complete", "reopen", "cancel")) {
      audited(
          "POST " + W + "/tasks/{id}/" + action,
          o,
          me,
          post(W + "/tasks/{id}/" + action, w, task),
          Map.of("version", version(o, W + "/tasks/{id}", w, task)),
          action.toUpperCase(),
          "TASK");
    }
    long activity =
        audited(
                "POST " + W + "/activities",
                o,
                me,
                post(W + "/activities", w),
                Map.of("type", "NOTE", "subject", "Note", "companyId", ids.get("companies")),
                "CREATE",
                "ACTIVITY")
            .get("id")
            .asLong();
    audited(
        "PUT " + W + "/activities/{id}",
        o,
        me,
        put(W + "/activities/{id}", w, activity),
        Map.of("type", "CALL", "subject", "Call", "companyId", ids.get("companies"), "version", 0),
        "UPDATE",
        "ACTIVITY");
    audited(
        "DELETE " + W + "/activities/{id}",
        o,
        me,
        delete(W + "/activities/{id}", w, activity),
        null,
        "DELETE",
        "ACTIVITY");

    for (String c : List.of("companies", "contacts", "leads", "deals")) {
      audited(
          "GET " + W + "/" + c + "/export",
          o,
          me,
          get(W + "/" + c + "/export", w),
          null,
          "EXPORT",
          "WORKSPACE");
    }
    audited(
        "DELETE " + W + "/pipeline-stages/{id}",
        o,
        me,
        delete(W + "/pipeline-stages/{id}", w, stage),
        null,
        "DELETE",
        "PIPELINE_STAGE");
    audited(
        "DELETE " + W + "/lead-sources/{id}",
        o,
        me,
        delete(W + "/lead-sources/{id}", w, source),
        null,
        "DELETE",
        "LEAD_SOURCE");

    // Members: invitations, joining both ways, a role change, a removal and a leave.
    String x = uniqueEmail();
    String linkX =
        audited(
                "POST " + W + "/invitations",
                o,
                me,
                post(W + "/invitations", w),
                Map.of("email", x, "role", "SALES_REP"),
                "INVITE",
                "INVITATION")
            .get("token")
            .asText();
    JsonNode joined =
        auditedAs(
            "POST /api/v1/invitations/{token}/register",
            null,
            x,
            post("/api/v1/invitations/{t}/register", linkX),
            Map.of("password", PASSWORD),
            "JOIN",
            "MEMBER");
    String y = uniqueEmail();
    String yToken = tokenFor(y);
    String linkY =
        body(send(o, post(W + "/invitations", w), Map.of("email", y, "role", "SALES_REP")))
            .get("token")
            .asText();
    auditedAs(
        "POST /api/v1/invitations/{token}/accept",
        yToken,
        y,
        post("/api/v1/invitations/{t}/accept", linkY),
        null,
        "JOIN",
        "MEMBER");
    long z =
        body(send(
                o,
                post(W + "/invitations", w),
                Map.of("email", uniqueEmail(), "role", "SALES_REP")))
            .get("id")
            .asLong();
    audited(
        "DELETE " + W + "/invitations/{id}",
        o,
        me,
        delete(W + "/invitations/{id}", w, z),
        null,
        "INVITE_REVOKE",
        "INVITATION");
    long xId = userId(x);
    audited(
        "PUT " + W + "/members/{userId}",
        o,
        me,
        put(W + "/members/{u}", w, xId),
        Map.of("role", "MANAGER"),
        "ROLE_CHANGE",
        "MEMBER");
    audited(
        "DELETE " + W + "/members/{userId}",
        o,
        me,
        delete(W + "/members/{u}", w, xId),
        null,
        "MEMBER_REMOVE",
        "MEMBER");
    auditedAs(
        "DELETE " + W + "/members/{userId}",
        yToken,
        y,
        delete(W + "/members/{u}", w, userId(y)),
        null,
        "LEAVE",
        "MEMBER");
    assertThat(joined.get("accessToken").asText()).isNotBlank();

    assertThat(wrong).as("changes without their audit event").isEmpty();
    Set<String> changing = new TreeSet<>();
    mappings
        .getHandlerMethods()
        .keySet()
        .forEach(
            info -> {
              for (String p : info.getPathPatternsCondition().getPatternValues()) {
                if (!p.startsWith("/api/v1/workspaces") && !p.startsWith("/api/v1/invitations"))
                  continue;
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                  if (m != RequestMethod.GET || p.endsWith("/export")) changing.add(m + " " + p);
                }
              }
            });
    assertThat(covered).as("changing endpoints this probe ran").containsAll(changing);
  }

  private JsonNode audited(
      String endpoint,
      String token,
      long actor,
      MockHttpServletRequestBuilder r,
      Object body,
      String action,
      String type)
      throws Exception {
    return run(endpoint, token, actor, r, body, action, type);
  }

  /** As {@link #audited} for a caller known by email (a token, or none for the public link). */
  private JsonNode auditedAs(
      String endpoint,
      String token,
      String email,
      MockHttpServletRequestBuilder r,
      Object body,
      String action,
      String type)
      throws Exception {
    if (token == null) {
      r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
      long before = lastEvent();
      MockHttpServletResponse res = mvc.perform(r).andReturn().getResponse();
      check(endpoint, res, before, userId(email), action, type);
      return json.readTree(res.getContentAsString());
    }
    return run(endpoint, token, userId(email), r, body, action, type);
  }

  private JsonNode run(
      String endpoint,
      String token,
      long actor,
      MockHttpServletRequestBuilder r,
      Object body,
      String action,
      String type)
      throws Exception {
    long before = lastEvent();
    MockHttpServletResponse res = send(token, r, body).andReturn().getResponse();
    check(endpoint, res, before, actor, action, type);
    String text = res.getContentAsString();
    return text.isEmpty() || !text.startsWith("{") ? json.nullNode() : json.readTree(text);
  }

  private void check(
      String endpoint,
      MockHttpServletResponse res,
      long before,
      long actor,
      String action,
      String type)
      throws Exception {
    covered.add(endpoint);
    if (res.getStatus() >= 300) {
      wrong.add(endpoint + " -> " + res.getStatus() + " " + res.getContentAsString());
      return;
    }
    Long found =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_events WHERE id > ? AND action = ? AND entity_type = ? AND actor_id = ?",
            Long.class,
            before,
            action,
            type,
            actor);
    if (found == null || found == 0)
      wrong.add(endpoint + " wrote no " + action + " " + type + " by " + actor);
  }

  private long lastEvent() {
    Long id = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM audit_events", Long.class);
    return id == null ? 0 : id;
  }

  private long version(String token, String path, long w, long id) throws Exception {
    return body(send(token, get(path, w, id))).get("version").asLong();
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }
}
