package com.example.boilerplate.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AuditEventIT extends WorkspaceTestSupport {
  static final String EVENTS = "/api/v1/workspaces/{workspaceId}/audit-events";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  @Test
  void ownerReadsWholeTrail() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    send(token, put("/api/v1/users/me"), Map.of("displayName", "Olivia Owner"))
        .andExpect(status().isOk());
    long w = createWorkspace(token, "Acme");
    long company = createCompany(token, w, "Globex");
    send(
            token,
            put(COMPANIES + "/{id}", w, company),
            Map.of("name", "Globex Corp", "ownerId", userId(email), "version", 0))
        .andExpect(status().isOk());

    List<AuditEvent> stored = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w);
    assertThat(stored).hasSize(3);
    JsonNode page = body(send(token, get(EVENTS, w)).andExpect(status().isOk()));

    // Newest first, id as the tie-breaker: the order of the stored trail.
    assertThat(ids(page)).isEqualTo(stored.stream().map(AuditEvent::getId).toList());
    assertThat(page.get("totalElements").asLong()).isEqualTo(3);
    JsonNode update = page.get("content").get(0);
    assertThat(update.get("action").asText()).isEqualTo("UPDATE");
    assertThat(update.get("entityType").asText()).isEqualTo("COMPANY");
    assertThat(update.get("entityId").asLong()).isEqualTo(company);
    assertThat(update.get("entityLabel").asText()).isEqualTo("Globex Corp");
    assertThat(update.get("occurredAt").asText()).isNotEmpty();
    assertThat(update.get("actor").get("userId").asLong()).isEqualTo(userId(email));
    assertThat(update.get("actor").get("email").asText()).isEqualTo(email);
    assertThat(update.get("actor").get("displayName").asText()).isEqualTo("Olivia Owner");
    assertThat(update.get("changes").get("name").get("from").asText()).isEqualTo("Globex");
    assertThat(update.get("changes").get("name").get("to").asText()).isEqualTo("Globex Corp");
    JsonNode create = page.get("content").get(1);
    assertThat(create.get("action").asText()).isEqualTo("CREATE");
    assertThat(create.get("entityLabel").asText()).isEqualTo("Globex");
    assertThat(create.get("changes").isEmpty()).isTrue();
    JsonNode workspace = page.get("content").get(2);
    assertThat(workspace.get("entityType").asText()).isEqualTo("WORKSPACE");
    assertThat(workspace.get("entityId").asLong()).isEqualTo(w);

    // An admin reads the trail too; a manager has no AUDIT_READ.
    String admin = uniqueEmail();
    String adminToken = tokenFor(admin);
    addMember(w, admin, WorkspaceRole.ADMIN);
    send(adminToken, get(EVENTS, w)).andExpect(status().isOk()).andExpect(totalElements(3));
    String manager = uniqueEmail();
    String managerToken = tokenFor(manager);
    addMember(w, manager, WorkspaceRole.MANAGER);
    send(managerToken, get(EVENTS, w)).andExpect(status().isForbidden());
  }

  @Test
  void filtersAndPaging() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long first = createCompany(token, w, "First");
    long second = createCompany(token, w, "Second");
    String other = uniqueEmail();
    tokenFor(other);
    addMember(w, other, WorkspaceRole.ADMIN);
    long otherId = userId(other);
    jdbc.update(
        "INSERT INTO audit_events (workspace_id, occurred_at, actor_id, actor_email, action,"
            + " entity_type, entity_id, entity_label, changes)"
            + " VALUES (?, now(), ?, ?, 'ARCHIVE', 'COMPANY', ?, 'First', '{}'::jsonb)",
        w,
        otherId,
        other,
        first);

    // 1 workspace + 2 companies + 1 archive.
    assertThat(total(token, w, "")).isEqualTo(4);
    assertThat(total(token, w, "?entityType=COMPANY")).isEqualTo(3);
    assertThat(total(token, w, "?entityType=WORKSPACE")).isEqualTo(1);
    assertThat(total(token, w, "?entityType=COMPANY&entityId=" + first)).isEqualTo(2);
    assertThat(total(token, w, "?entityType=COMPANY&entityId=" + second)).isEqualTo(1);
    // entityId alone narrows too (an id may exist under several types: ids are per table).
    assertThat(total(token, w, "?entityId=" + second))
        .isEqualTo(
            events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
                .filter(e -> e.getEntityId() == second)
                .count());
    assertThat(total(token, w, "?actorId=" + otherId)).isEqualTo(1);
    assertThat(total(token, w, "?actorId=" + userId(email))).isEqualTo(3);
    assertThat(total(token, w, "?entityType=COMPANY&entityId=" + first + "&actorId=" + otherId))
        .isEqualTo(1);
    assertThat(total(token, w, "?entityType=LEAD")).isZero();

    List<Long> all = ids(body(send(token, get(EVENTS + "?size=2", w)).andExpect(status().isOk())));
    List<Long> pageTwo =
        ids(body(send(token, get(EVENTS + "?size=2&page=1", w)).andExpect(status().isOk())));
    assertThat(all).hasSize(2);
    assertThat(pageTwo).hasSize(2);
    List<Long> everything = new ArrayList<>(all);
    everything.addAll(pageTwo);
    assertThat(everything)
        .isEqualTo(
            events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
                .map(AuditEvent::getId)
                .toList());
  }

  @Test
  void sameInstantListsTheHigherIdFirst() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Timestamp at = Timestamp.from(Instant.now().plusSeconds(3600));
    for (String label : List.of("one", "two", "three")) {
      jdbc.update(
          "INSERT INTO audit_events (workspace_id, occurred_at, action, entity_type, entity_id,"
              + " entity_label, changes) VALUES (?, ?, 'UPDATE', 'COMPANY', 7, ?, '{}'::jsonb)",
          w,
          at,
          label);
    }

    JsonNode content =
        body(send(token, get(EVENTS + "?size=3", w)).andExpect(status().isOk())).get("content");

    assertThat(content)
        .extracting(n -> n.get("entityLabel").asText())
        .containsExactly("three", "two", "one");
    assertThat(content.get(0).get("id").asLong()).isGreaterThan(content.get(1).get("id").asLong());
  }

  @Test
  void actorIsLeftOutWithoutOneAndHasNoNameOnceTheUserIsGone() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long goneUser = 987_654_321L;
    jdbc.update(
        "INSERT INTO audit_events (workspace_id, occurred_at, actor_id, actor_email, action,"
            + " entity_type, entity_id, entity_label, changes)"
            + " VALUES (?, now() + interval '1 hour', ?, 'gone@example.com', 'UPDATE', 'COMPANY',"
            + " 1, 'Left behind', '{}'::jsonb)",
        w,
        goneUser);
    jdbc.update(
        "INSERT INTO audit_events (workspace_id, occurred_at, action, entity_type, entity_id,"
            + " entity_label, changes)"
            + " VALUES (?, now() + interval '2 hours', 'UPDATE', 'COMPANY', 2, 'System', '{}'::jsonb)",
        w);

    JsonNode content = body(send(token, get(EVENTS, w)).andExpect(status().isOk())).get("content");

    JsonNode system = content.get(0);
    assertThat(system.get("entityLabel").asText()).isEqualTo("System");
    assertThat(system.has("actor")).isFalse();
    JsonNode gone = content.get(1);
    assertThat(gone.get("entityLabel").asText()).isEqualTo("Left behind");
    assertThat(gone.get("actor").get("userId").asLong()).isEqualTo(goneUser);
    assertThat(gone.get("actor").get("email").asText()).isEqualTo("gone@example.com");
    assertThat(gone.get("actor").has("displayName")).isFalse();
  }

  @Test
  void repReadsOneRecordHistoryOnly() throws Exception {
    String owner = uniqueEmail();
    String ownerToken = tokenFor(owner);
    long w = createWorkspace(ownerToken, "Acme");
    String rep = uniqueEmail();
    String repToken = tokenFor(rep);
    addMember(w, rep, WorkspaceRole.SALES_REP);
    long company = createCompany(ownerToken, w, "Globex");

    // The history of a record: every member.
    send(repToken, get(EVENTS + "?entityType=COMPANY&entityId=" + company, w))
        .andExpect(status().isOk())
        .andExpect(totalElements(1))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.content[0].entityId").value(company));
    // The whole trail, or a part of it that is not one record's history: AUDIT_READ.
    for (String query :
        List.of(
            "",
            "?entityType=COMPANY",
            "?entityId=" + company,
            "?actorId=" + userId(owner),
            "?size=5")) {
      send(repToken, get(EVENTS + query, w))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    // Each record type's history is a record history; the other types' (who joined, who got which
    // role, the pipeline) are not.
    List<AuditEntityType> recordTypes =
        List.of(
            AuditEntityType.COMPANY,
            AuditEntityType.CONTACT,
            AuditEntityType.LEAD,
            AuditEntityType.DEAL,
            AuditEntityType.TASK,
            AuditEntityType.ACTIVITY);
    for (AuditEntityType type : AuditEntityType.values()) {
      send(repToken, get(EVENTS + "?entityType=" + type + "&entityId=" + w, w))
          .andExpect(recordTypes.contains(type) ? status().isOk() : status().isForbidden());
    }
    // The permission comes first: without one record a bad page is still a 403, with one a 400.
    send(repToken, get(EVENTS + "?size=0", w)).andExpect(status().isForbidden());
    send(repToken, get(EVENTS + "?entityType=COMPANY&entityId=1&size=0", w))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.size").exists());
    // The owner reads those.
    send(ownerToken, get(EVENTS + "?entityType=WORKSPACE&entityId=" + w, w))
        .andExpect(status().isOk())
        .andExpect(totalElements(1));
  }

  @Test
  void otherWorkspaceEventsNeverAppear() throws Exception {
    String ownerA = uniqueEmail();
    String tokenA = tokenFor(ownerA);
    long a = createWorkspace(tokenA, "A");
    long companyA = createCompany(tokenA, a, "Only in A");
    String ownerB = uniqueEmail();
    String tokenB = tokenFor(ownerB);
    long b = createWorkspace(tokenB, "B");
    long companyB = createCompany(tokenB, b, "Only in B");
    // A rep of A, to check the record-history path as well as the AUDIT_READ one.
    String rep = uniqueEmail();
    String repToken = tokenFor(rep);
    addMember(a, rep, WorkspaceRole.SALES_REP);

    assertThat(ids(body(send(tokenA, get(EVENTS, a)).andExpect(status().isOk()))))
        .isEqualTo(storedIds(a))
        .hasSize(2);
    assertThat(ids(body(send(tokenB, get(EVENTS, b)).andExpect(status().isOk()))))
        .isEqualTo(storedIds(b))
        .hasSize(2);
    assertThat(storedIds(a)).doesNotContainAnyElementsOf(storedIds(b));

    // The other workspace's record and actor, asked for in this one: nothing.
    send(tokenA, get(EVENTS + "?entityType=COMPANY&entityId=" + companyB, a))
        .andExpect(status().isOk())
        .andExpect(totalElements(0));
    send(tokenA, get(EVENTS + "?entityType=WORKSPACE&entityId=" + b, a))
        .andExpect(status().isOk())
        .andExpect(totalElements(0));
    send(tokenA, get(EVENTS + "?actorId=" + userId(ownerB), a))
        .andExpect(status().isOk())
        .andExpect(totalElements(0));
    send(repToken, get(EVENTS + "?entityType=COMPANY&entityId=" + companyB, a))
        .andExpect(status().isOk())
        .andExpect(totalElements(0));
    send(tokenB, get(EVENTS + "?entityType=COMPANY&entityId=" + companyA, b))
        .andExpect(status().isOk())
        .andExpect(totalElements(0));

    // And a member of one is a stranger to the other's trail.
    send(tokenA, get(EVENTS, b)).andExpect(status().isNotFound());
    send(tokenB, get(EVENTS + "?entityType=COMPANY&entityId=" + companyB, a))
        .andExpect(status().isNotFound());
  }

  @Test
  void badParametersAre400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    for (String query :
        List.of(
            "?entityType=NOPE",
            "?entityId=abc",
            "?actorId=x",
            "?size=0",
            "?size=101",
            "?page=-1")) {
      send(token, get(EVENTS + query, w)).andExpect(status().isBadRequest());
    }
    // A malformed entityType is a parameter of the wrong type: no code.
    send(token, get(EVENTS + "?entityType=NOPE", w)).andExpect(jsonPath("$.code").doesNotExist());
    // page × size past the query's int offset.
    send(token, get(EVENTS + "?page=2147483647&size=2", w))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.page").exists());
  }

  private long total(String token, long w, String query) throws Exception {
    return body(send(token, get(EVENTS + query, w)).andExpect(status().isOk()))
        .get("totalElements")
        .asLong();
  }

  private static ResultMatcher totalElements(long n) {
    return jsonPath("$.totalElements").value(n);
  }

  private List<Long> storedIds(long w) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .map(AuditEvent::getId)
        .toList();
  }

  private static List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.get("content").forEach(n -> ids.add(n.get("id").asLong()));
    return ids;
  }

  private long createCompany(String token, long w, String name) throws Exception {
    MockHttpServletRequestBuilder request = post(COMPANIES, w);
    return body(send(token, request, Map.of("name", name)).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }
}
