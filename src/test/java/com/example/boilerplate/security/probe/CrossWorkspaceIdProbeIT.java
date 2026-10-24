package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Security review probe (phase 4): a member of two workspaces, owner of A and a sales rep in B,
 * uses A's path with B's ids. Every id-bearing endpoint (taken from the running application, so a
 * new one is covered or fails here) is a 404 from the service, every link in a body a 400 on its
 * field, every filter finds nothing of B's, and B is unchanged afterwards. The caller can read B's
 * ids legitimately through B's path: this is the insider case, not id guessing.
 */
class CrossWorkspaceIdProbeIT extends WorkspaceTestSupport {
  private static final String PREFIX = "/api/v1/workspaces/{workspaceId}";

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Autowired JdbcTemplate jdbc;

  /** One of each record of a workspace, plus whatever the probe needs about it. */
  record Fixture(
      long workspace,
      long ownerUser,
      long company,
      long contact,
      long lead,
      long deal,
      long task,
      long activity,
      long stage,
      long source,
      long invitation,
      long member,
      List<Long> openStages) {
    long id(String collection) {
      return switch (collection) {
        case "companies" -> company;
        case "contacts" -> contact;
        case "leads" -> lead;
        case "deals" -> deal;
        case "tasks" -> task;
        case "activities" -> activity;
        case "pipeline-stages" -> stage;
        case "lead-sources" -> source;
        case "invitations" -> invitation;
        case "members" -> member;
        default -> throw new IllegalArgumentException("no fixture id for " + collection);
      };
    }
  }

  private final String mark = "bmark" + UUID.randomUUID().toString().substring(0, 8);
  private final String tag = mark + "tag";
  private String bOwnerEmail;

  @Test
  void anotherWorkspacesIdsAreNeverReachableThroughOnesOwnWorkspace() throws Exception {
    String callerEmail = uniqueEmail();
    String caller = tokenFor(callerEmail);
    long callerId = users.findByEmail(callerEmail).orElseThrow().getId();

    bOwnerEmail = uniqueEmail();
    String bOwner = tokenFor(bOwnerEmail);
    long wb = createWorkspace(bOwner, "B " + mark);
    addMember(wb, callerEmail, WorkspaceRole.SALES_REP);
    Fixture b = workspaceB(bOwner, wb, callerId);
    Map<String, JsonNode> bBefore = snapshot(bOwner, b);

    long wa = createWorkspace(caller, "A");
    Fixture a = workspaceA(caller, wa, callerId);

    List<String> wrong = new ArrayList<>();
    pathIds(caller, a, b, wrong);
    bodyLinks(caller, a, b, wrong);
    filters(caller, a, b, wrong);

    assertThat(wrong).as("cross-workspace answers that are not a 404 / 400 / empty").isEmpty();

    // B is untouched: same records at the same versions, and nothing in its trail by the caller.
    assertThat(snapshot(bOwner, b)).isEqualTo(bBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE workspace_id = ? AND actor_id = ?",
                Long.class,
                wb,
                callerId))
        .isZero();
    // A's lead is still unconverted: the failed conversions left nothing behind.
    assertThat(body(send(caller, get(PREFIX + "/leads/{id}", wa, a.lead()))).get("status").asText())
        .isEqualTo("QUALIFIED");
  }

  /** Every endpoint with an id in its path, A's workspace and B's id: the service's 404. */
  private void pathIds(String caller, Fixture a, Fixture b, List<String> wrong) throws Exception {
    List<String> seen = new ArrayList<>();
    for (var info : mappings.getHandlerMethods().keySet()) {
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (!pattern.startsWith(PREFIX + "/")) continue;
        String rest = pattern.substring(PREFIX.length());
        if (!rest.matches(".*\\{[^}]+}.*")) continue;
        for (RequestMethod m : info.getMethodsCondition().getMethods()) {
          seen.add(m + " " + rest);
          String collection = rest.split("/")[1];
          String path =
              pattern
                  .replace("{workspaceId}", String.valueOf(a.workspace()))
                  .replaceAll("\\{[^/]+}", String.valueOf(b.id(collection)));
          Object body = pathBody(m + " " + rest, a);
          MockHttpServletRequestBuilder r = request(HttpMethod.valueOf(m.name()), path);
          MockHttpServletResponse res = send(caller, r, body).andReturn().getResponse();
          JsonNode answer = json.readTree(res.getContentAsString());
          if (res.getStatus() != 404
              || !"NOT_FOUND".equals(answer.path("code").asText())
              || answer.path("message").asText().equals("Workspace not found")) {
            wrong.add(m + " " + rest + " with B's id -> " + res.getStatus() + " " + answer);
          }
        }
      }
    }
    // The endpoints there are today: a new one must get a body below (or the scan missed it).
    assertThat(seen).hasSizeGreaterThanOrEqualTo(35);
  }

  /** A body that passes validation, so only the id decides; links name A's own records. */
  private Object pathBody(String endpoint, Fixture a) {
    return switch (endpoint) {
      case "GET /companies/{id}",
          "POST /companies/{id}/archive",
          "POST /companies/{id}/restore",
          "GET /contacts/{id}",
          "POST /contacts/{id}/archive",
          "POST /contacts/{id}/restore",
          "GET /leads/{id}",
          "POST /leads/{id}/archive",
          "POST /leads/{id}/restore",
          "GET /deals/{id}",
          "GET /deals/{id}/history",
          "POST /deals/{id}/archive",
          "POST /deals/{id}/restore",
          "GET /tasks/{id}",
          "GET /activities/{id}",
          "DELETE /activities/{id}",
          "DELETE /pipeline-stages/{id}",
          "DELETE /lead-sources/{id}",
          "DELETE /invitations/{id}",
          "DELETE /members/{userId}" ->
          null;
      case "PUT /companies/{id}" -> Map.of("name", "Hijacked", "version", 0);
      case "PUT /contacts/{id}", "PUT /leads/{id}" -> Map.of("firstName", "Hijacked", "version", 0);
      case "POST /leads/{id}/status" -> Map.of("status", "CONTACTED", "version", 0);
      case "POST /leads/{id}/convert" -> Map.of("createCompany", false);
      case "PUT /deals/{id}" -> Map.of("name", "Hijacked", "amount", "1", "version", 0);
      case "POST /deals/{id}/move" -> Map.of("stageId", a.openStages().get(1), "version", 0);
      case "PUT /tasks/{id}" -> Map.of("title", "Hijacked", "version", 0);
      case "POST /tasks/{id}/complete", "POST /tasks/{id}/reopen", "POST /tasks/{id}/cancel" ->
          Map.of("version", 0);
      case "PUT /activities/{id}" ->
          Map.of("type", "NOTE", "subject", "Hijacked", "companyId", a.company(), "version", 0);
      case "PUT /pipeline-stages/{id}" ->
          Map.of("name", "Hijacked", "probability", 5, "version", 0);
      case "PUT /lead-sources/{id}" -> Map.of("name", "Hijacked", "archived", true, "version", 0);
      case "PUT /members/{userId}" -> Map.of("role", "MANAGER");
      default -> throw new AssertionError("add a valid body for " + endpoint);
    };
  }

  /** B's records named in A's request bodies: a 400 on that field, nothing written. */
  private void bodyLinks(String caller, Fixture a, Fixture b, List<String> wrong) throws Exception {
    long w = a.workspace();
    String co = PREFIX + "/companies";
    String ct = PREFIX + "/contacts";
    String ld = PREFIX + "/leads";
    String dl = PREFIX + "/deals";
    String tk = PREFIX + "/tasks";
    String ac = PREFIX + "/activities";
    long me = a.ownerUser();
    record Row(MockHttpServletRequestBuilder request, Map<String, Object> body, String field) {}
    List<Row> rows = new ArrayList<>();
    rows.add(new Row(post(co, w), Map.of("name", "X", "ownerId", b.ownerUser()), "ownerId"));
    rows.add(
        new Row(
            put(co + "/{id}", w, a.company()),
            Map.of(
                "name",
                "X",
                "ownerId",
                b.ownerUser(),
                "version",
                version(caller, co, w, a.company())),
            "ownerId"));
    for (var link :
        Map.of("companyId", b.company(), "sourceId", b.source(), "ownerId", b.ownerUser())
            .entrySet()) {
      rows.add(
          new Row(
              post(ct, w),
              Map.of("firstName", "X", link.getKey(), link.getValue()),
              link.getKey()));
    }
    for (var link : Map.of("companyId", b.company(), "sourceId", b.source()).entrySet()) {
      rows.add(
          new Row(
              put(ct + "/{id}", w, a.contact()),
              Map.of(
                  "firstName",
                  "X",
                  "ownerId",
                  me,
                  link.getKey(),
                  link.getValue(),
                  "version",
                  version(caller, ct, w, a.contact())),
              link.getKey()));
    }
    for (var link : Map.of("sourceId", b.source(), "ownerId", b.ownerUser()).entrySet()) {
      rows.add(
          new Row(
              post(ld, w),
              Map.of("firstName", "X", link.getKey(), link.getValue()),
              link.getKey()));
    }
    rows.add(
        new Row(
            put(ld + "/{id}", w, a.lead()),
            Map.of(
                "firstName",
                "X",
                "ownerId",
                me,
                "sourceId",
                b.source(),
                "version",
                version(caller, ld, w, a.lead())),
            "sourceId"));
    Map<String, Long> dealLinks =
        Map.of(
            "stageId", b.stage(),
            "companyId", b.company(),
            "contactId", b.contact(),
            "sourceId", b.source(),
            "ownerId", b.ownerUser());
    for (var link : dealLinks.entrySet()) {
      rows.add(
          new Row(
              post(dl, w),
              Map.of("name", "X", "amount", "1", link.getKey(), link.getValue()),
              link.getKey()));
    }
    for (String field : List.of("companyId", "contactId", "sourceId")) {
      rows.add(
          new Row(
              put(dl + "/{id}", w, a.deal()),
              Map.of(
                  "name",
                  "X",
                  "amount",
                  "1",
                  "ownerId",
                  me,
                  field,
                  dealLinks.get(field),
                  "version",
                  version(caller, dl, w, a.deal())),
              field));
    }
    rows.add(
        new Row(
            post(dl + "/{id}/move", w, a.deal()),
            Map.of("stageId", b.stage(), "version", version(caller, dl, w, a.deal())),
            "stageId"));
    Map<String, Long> recordLinks =
        Map.of(
            "companyId", b.company(),
            "contactId", b.contact(),
            "leadId", b.lead(),
            "dealId", b.deal());
    for (var link : recordLinks.entrySet()) {
      rows.add(
          new Row(
              post(tk, w), Map.of("title", "X", link.getKey(), link.getValue()), link.getKey()));
      rows.add(
          new Row(
              put(tk + "/{id}", w, a.task()),
              Map.of(
                  "title",
                  "X",
                  "assigneeId",
                  me,
                  link.getKey(),
                  link.getValue(),
                  "version",
                  version(caller, tk, w, a.task())),
              link.getKey()));
      rows.add(
          new Row(
              post(ac, w),
              Map.of("type", "NOTE", "subject", "X", link.getKey(), link.getValue()),
              link.getKey()));
      rows.add(
          new Row(
              put(ac + "/{id}", w, a.activity()),
              Map.of(
                  "type",
                  "NOTE",
                  "subject",
                  "X",
                  link.getKey(),
                  link.getValue(),
                  "version",
                  version(caller, ac, w, a.activity())),
              link.getKey()));
    }
    rows.add(new Row(post(tk, w), Map.of("title", "X", "assigneeId", b.ownerUser()), "assigneeId"));
    rows.add(
        new Row(
            put(PREFIX + "/pipeline-stages/order", w),
            Map.of("stageIds", b.openStages()),
            "stageIds"));
    rows.add(
        new Row(
            post(ld + "/{id}/convert", w, a.lead()),
            Map.of("createCompany", false, "companyId", b.company()),
            "companyId"));
    rows.add(
        new Row(
            post(ld + "/{id}/convert", w, a.lead()),
            Map.of("createCompany", false, "contactId", b.contact()),
            "contactId"));
    rows.add(
        new Row(
            post(ld + "/{id}/convert", w, a.lead()),
            Map.of(
                "createCompany",
                false,
                "deal",
                Map.of("name", "X", "amount", "1", "stageId", b.stage())),
            "deal.stageId"));

    for (Row row : rows) {
      MockHttpServletResponse res =
          send(caller, row.request(), row.body()).andReturn().getResponse();
      JsonNode answer = json.readTree(res.getContentAsString());
      if (res.getStatus() != 400 || !answer.path("fieldErrors").has(row.field())) {
        var sent = row.request().buildRequest(new MockServletContext());
        wrong.add(
            sent.getMethod()
                + " "
                + sent.getRequestURI()
                + " "
                + row.body()
                + " -> "
                + res.getStatus()
                + " "
                + answer);
      }
    }
  }

  /** B's ids as filters of A's lists, exports and reads: nothing of B's comes back. */
  private void filters(String caller, Fixture a, Fixture b, List<String> wrong) throws Exception {
    long w = a.workspace();
    Map<String, String> lists = new HashMap<>();
    lists.put("/companies?ownerId=" + b.ownerUser(), "page");
    lists.put("/contacts?companyId=" + b.company(), "page");
    lists.put("/contacts?sourceId=" + b.source(), "page");
    lists.put("/contacts?ownerId=" + b.ownerUser(), "page");
    lists.put("/leads?sourceId=" + b.source(), "page");
    lists.put("/leads?ownerId=" + b.ownerUser(), "page");
    lists.put("/deals?stageId=" + b.stage(), "page");
    lists.put("/deals?companyId=" + b.company(), "page");
    lists.put("/deals?contactId=" + b.contact(), "page");
    lists.put("/deals?sourceId=" + b.source(), "page");
    lists.put("/deals?ownerId=" + b.ownerUser(), "page");
    lists.put("/tasks?companyId=" + b.company(), "page");
    lists.put("/tasks?contactId=" + b.contact(), "page");
    lists.put("/tasks?leadId=" + b.lead(), "page");
    lists.put("/tasks?dealId=" + b.deal(), "page");
    lists.put("/tasks?assigneeId=" + b.ownerUser(), "page");
    lists.put("/activities?companyId=" + b.company(), "page");
    lists.put("/activities?contactId=" + b.contact(), "page");
    lists.put("/activities?leadId=" + b.lead(), "page");
    lists.put("/activities?dealId=" + b.deal(), "page");
    lists.put("/activities?authorId=" + b.ownerUser(), "page");
    lists.put("/audit-events?entityType=COMPANY&entityId=" + b.company(), "page");
    lists.put("/audit-events?entityType=DEAL&entityId=" + b.deal(), "page");
    lists.put("/audit-events?actorId=" + b.ownerUser(), "page");
    lists.put("/companies/export?ownerId=" + b.ownerUser(), "csv");
    lists.put("/contacts/export?companyId=" + b.company(), "csv");
    lists.put("/leads/export?sourceId=" + b.source(), "csv");
    lists.put("/deals/export?companyId=" + b.company(), "csv");
    lists.put("/deals/board?companyId=" + b.company(), "board");
    // Reads without an id filter: none may show B's markers or B's owner.
    for (String path :
        List.of(
            "/companies",
            "/contacts",
            "/leads",
            "/deals",
            "/tasks",
            "/activities",
            "/audit-events",
            "/deals/board",
            "/contacts/tags",
            "/contacts/duplicates?email=" + mark + "@b.example",
            "/companies/duplicates?name=" + mark + "-co",
            "/members",
            "/invitations",
            "/pipeline-stages",
            "/lead-sources?includeArchived=true",
            "/reminders",
            "/dashboard",
            "/reports/pipeline",
            "/reports/won-lost",
            "/reports/funnel",
            "/reports/activities",
            "/reports/follow-ups",
            "/companies/export",
            "/contacts/export",
            "/leads/export",
            "/deals/export")) {
      lists.putIfAbsent(path, "text");
    }

    for (var e : lists.entrySet()) {
      String url = "/api/v1/workspaces/" + w + e.getKey();
      MockHttpServletResponse res = send(caller, get(url)).andReturn().getResponse();
      String text = res.getContentAsString();
      boolean ok =
          res.getStatus() == 200
              && !text.contains(mark)
              && !text.contains(bOwnerEmail)
              && switch (e.getValue()) {
                case "page" -> json.readTree(text).get("totalElements").asLong() == 0;
                case "csv" -> text.strip().lines().count() == 1;
                case "board" -> {
                  boolean empty = true;
                  for (JsonNode column : json.readTree(text).get("stages")) {
                    empty &= column.get("count").asLong() == 0;
                  }
                  yield empty;
                }
                default -> true;
              };
      if (!ok) wrong.add("GET " + e.getKey() + " -> " + res.getStatus() + " " + text);
    }
  }

  /** What B looks like to its owner: each record by id, its settings, members and invitations. */
  private Map<String, JsonNode> snapshot(String owner, Fixture b) throws Exception {
    Map<String, JsonNode> s = new HashMap<>();
    for (String c : List.of("companies", "contacts", "leads", "deals", "tasks", "activities")) {
      s.put(c, body(send(owner, get(PREFIX + "/" + c + "/{id}", b.workspace(), b.id(c)))));
    }
    for (String c : List.of("pipeline-stages", "lead-sources?includeArchived=true", "members")) {
      s.put(c, body(send(owner, get("/api/v1/workspaces/" + b.workspace() + "/" + c))));
    }
    s.put(
        "invitations",
        body(send(owner, get(PREFIX + "/invitations", b.workspace()))).get(0).get("id"));
    return s;
  }

  private Fixture workspaceB(String owner, long w, long callerId) throws Exception {
    String today = LocalDate.now().toString();
    long company = create(owner, w, "companies", Map.of("name", mark + "-co"));
    long contact =
        create(
            owner,
            w,
            "contacts",
            Map.of(
                "firstName",
                mark + "-ct",
                "email",
                mark + "@b.example",
                "companyId",
                company,
                "tags",
                List.of(tag)));
    long source = create(owner, w, "lead-sources", Map.of("name", mark + "-src"));
    long lead =
        create(
            owner,
            w,
            "leads",
            Map.of(
                "firstName", mark + "-ld",
                "ownerId", callerId,
                "followUpDate", today,
                "sourceId", source));
    long deal =
        create(
            owner,
            w,
            "deals",
            Map.of(
                "name", mark + "-dl", "amount", "100", "companyId", company, "contactId", contact));
    long task =
        create(
            owner,
            w,
            "tasks",
            Map.of(
                "title",
                mark + "-tk",
                "assigneeId",
                callerId,
                "dueAt",
                Instant.now().minus(1, ChronoUnit.HOURS).toString(),
                "companyId",
                company));
    long activity =
        create(
            owner,
            w,
            "activities",
            Map.of("type", "NOTE", "subject", mark + "-ac", "companyId", company));
    long stage =
        create(owner, w, "pipeline-stages", Map.of("name", mark + "-stg", "probability", 10));
    long invitation =
        create(owner, w, "invitations", Map.of("email", uniqueEmail(), "role", "SALES_REP"));
    String memberEmail = uniqueEmail();
    tokenFor(memberEmail);
    addMember(w, memberEmail, WorkspaceRole.MANAGER);
    long member = users.findByEmail(memberEmail).orElseThrow().getId();
    long ownerUser = users.findByEmail(bOwnerEmail).orElseThrow().getId();
    return new Fixture(
        w,
        ownerUser,
        company,
        contact,
        lead,
        deal,
        task,
        activity,
        stage,
        source,
        invitation,
        member,
        openStages(owner, w));
  }

  private Fixture workspaceA(String owner, long w, long ownerUser) throws Exception {
    long company = create(owner, w, "companies", Map.of("name", "A company"));
    long contact = create(owner, w, "contacts", Map.of("firstName", "A contact"));
    long lead = create(owner, w, "leads", Map.of("firstName", "A lead"));
    send(
        owner,
        post(PREFIX + "/leads/{id}/status", w, lead),
        Map.of("status", "QUALIFIED", "version", 0));
    long deal = create(owner, w, "deals", Map.of("name", "A deal", "amount", "1"));
    long task = create(owner, w, "tasks", Map.of("title", "A task"));
    long activity =
        create(
            owner, w, "activities", Map.of("type", "NOTE", "subject", "A", "companyId", company));
    return new Fixture(
        w,
        ownerUser,
        company,
        contact,
        lead,
        deal,
        task,
        activity,
        0,
        0,
        0,
        0,
        openStages(owner, w));
  }

  private long create(String token, long w, String collection, Map<String, Object> body)
      throws Exception {
    MockHttpServletResponse res =
        send(token, post(PREFIX + "/" + collection, w), body).andReturn().getResponse();
    assertThat(res.getStatus())
        .as("create %s: %s", collection, res.getContentAsString())
        .isEqualTo(201);
    return json.readTree(res.getContentAsString()).get("id").asLong();
  }

  private List<Long> openStages(String token, long w) throws Exception {
    List<Long> ids = new ArrayList<>();
    for (JsonNode s : body(send(token, get(PREFIX + "/pipeline-stages", w)))) {
      if (s.get("kind").asText().equals("OPEN")) ids.add(s.get("id").asLong());
    }
    return ids;
  }

  private long version(String token, String collection, long w, long id) throws Exception {
    return body(send(token, get(collection + "/{id}", w, id))).get("version").asLong();
  }
}
