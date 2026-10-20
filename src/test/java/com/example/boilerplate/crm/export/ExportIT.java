package com.example.boilerplate.crm.export;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditEvent;
import com.example.boilerplate.audit.AuditEventRepository;
import com.example.boilerplate.support.TestClock;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * CSV export of the four record lists (api.md, Export). The test profile caps an export at 3 rows,
 * so a fourth one reaches the limit.
 */
class ExportIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";
  static final List<String> LISTS = List.of("companies", "contacts", "leads", "deals");
  // WorkspaceTestSupport creates every workspace in this zone.
  static final ZoneId ZONE = ZoneId.of("Europe/Paris");

  static final String COMPANY_HEADER =
      "Reference,Name,Industry,Website,Phone,Address,Employees,Owner,Contacts,Open deals,Created,"
          + "Updated,Archived";
  static final String CONTACT_HEADER =
      "Reference,First name,Last name,Email,Phone,Job title,Company,Owner,Source,Tags,Created,"
          + "Updated,Archived";
  static final String LEAD_HEADER =
      "Reference,First name,Last name,Company,Email,Phone,Status,Priority,Follow-up date,"
          + "Estimated value,Currency,Source,Owner,Created,Updated,Archived";
  static final String DEAL_HEADER =
      "Reference,Name,Amount,Currency,Stage,Status,Probability,Expected close,Company,Contact,"
          + "Owner,Closed,Lost reason,Created,Updated,Archived";

  static final ObjectMapper PLAIN = new ObjectMapper();

  @Autowired AuditEventRepository events;
  @Autowired TestClock clock;

  @AfterEach
  void unpinTheClock() {
    clock.unpin();
  }

  /**
   * The file is named for today in the workspace's zone, by the application's clock: 11:00 UTC on
   * 17 October is already the 18th in Pacific/Kiritimati (UTC+14), still the 17th in UTC and Paris.
   */
  @Test
  void theFileIsNamedForTodayInTheWorkspaceZone() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Kiritimati", "Pacific/Kiritimati");
    clock.pin(Instant.parse("2026-10-17T11:00:00Z"));

    for (String list : LISTS) {
      export(owner, w, list)
          .andExpect(status().isOk())
          .andExpect(
              header()
                  .string(
                      HttpHeaders.CONTENT_DISPOSITION,
                      "attachment; filename=\"" + list + "-2026-10-18.csv\""));
    }
  }

  @Test
  void managerExportsContactsWithFilters() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    send(manager, put("/api/v1/users/me"), Map.of("displayName", "Mia Manager"))
        .andExpect(status().isOk());
    long formula =
        id(
            create(
                owner,
                w,
                "companies",
                Map.of("name", "=HYPERLINK(\"http://evil.example\",\"x\")")));
    long thai =
        id(
            create(
                manager,
                w,
                "contacts",
                Map.of(
                    "firstName", "สมชาย",
                    "lastName", "ใจดี",
                    "email", "somchai@example.com",
                    "phone", "+66 81 234 5678",
                    "jobTitle", "ผู้จัดการ",
                    "companyId", formula,
                    "sourceId", sourceId(owner, w, "Website"),
                    "tags", List.of("vip", "bangkok"))));
    long ann =
        id(
            create(
                owner,
                w,
                "contacts",
                Map.of("firstName", "Ann", "lastName", "Archer", "tags", List.of("vip"))));
    create(owner, w, "contacts", Map.of("firstName", "Bob", "tags", List.of("other")));
    archive(
        owner,
        w,
        "contacts",
        id(create(owner, w, "contacts", Map.of("firstName", "Old", "tags", List.of("vip")))));

    LocalDate before = LocalDate.now(ZONE);
    MvcResult result =
        export(manager, w, "contacts", "tag", "VIP")
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=utf-8"))
            .andReturn();
    LocalDate after = LocalDate.now(ZONE);

    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE))
        .isEqualTo("text/csv;charset=UTF-8");
    // Excel needs the BOM to read UTF-8.
    assertThat(result.getResponse().getContentAsByteArray())
        .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .isIn(
            "attachment; filename=\"contacts-" + before + ".csv\"",
            "attachment; filename=\"contacts-" + after + ".csv\"");
    JsonNode a = record(owner, w, "contacts", ann);
    JsonNode t = record(owner, w, "contacts", thai);
    // The list's default order: last name, then first name.
    assertThat(lines(result))
        .containsExactly(
            CONTACT_HEADER,
            row(
                ref('P', ann),
                "Ann",
                "Archer",
                "",
                "",
                "",
                "",
                ownerEmail,
                "",
                "vip",
                a.get("createdAt").asText(),
                a.get("updatedAt").asText(),
                "no"),
            row(
                ref('P', thai),
                "สมชาย",
                "ใจดี",
                "somchai@example.com",
                "'+66 81 234 5678",
                "ผู้จัดการ",
                "\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"x\"\")\"",
                "Mia Manager",
                "Website",
                "\"bangkok; vip\"",
                t.get("createdAt").asText(),
                t.get("updatedAt").asText(),
                "no"));
  }

  /** Each export takes its list's filters and order: the same rows as the list, in its order. */
  @Test
  void everyExportListsWhatItsListLists() throws Exception {
    Fixture f = fixture();
    String o = f.owner;
    long w = f.w;
    String m = String.valueOf(f.manager);

    assertSameRows(o, w, "companies", List.of(f.coA, f.coB, f.coD));
    assertSameRows(o, w, "companies", List.of(f.coA), "q", "alp");
    assertSameRows(o, w, "companies", List.of(f.coB), "ownerId", m);
    assertSameRows(o, w, "companies", List.of(f.coD), "unassigned", "true");
    assertSameRows(o, w, "companies", List.of(f.coB), "industry", "RETAIL");
    assertSameRows(o, w, "companies", List.of(f.coG), "archived", "true");

    assertSameRows(o, w, "contacts", List.of(f.pA, f.pB, f.pC));
    assertSameRows(o, w, "contacts", List.of(f.pB), "q", "bak");
    assertSameRows(o, w, "contacts", List.of(f.pA), "companyId", String.valueOf(f.coA));
    assertSameRows(o, w, "contacts", List.of(f.pB), "ownerId", m);
    assertSameRows(o, w, "contacts", List.of(f.pC), "unassigned", "true");
    assertSameRows(o, w, "contacts", List.of(f.pB), "sourceId", String.valueOf(f.referral));
    assertSameRows(o, w, "contacts", List.of(f.pA, f.pC), "tag", "VIP");
    assertSameRows(o, w, "contacts", List.of(f.pD), "archived", "true");

    assertSameRows(o, w, "leads", List.of(f.lA, f.lB, f.lC));
    assertSameRows(o, w, "leads", List.of(f.lB), "q", "moss");
    assertSameRows(o, w, "leads", List.of(f.lA, f.lC), "status", "NEW");
    // status repeats; the priority narrows the three to one.
    assertSameRows(
        o, w, "leads", List.of(f.lB), "status", "NEW", "status", "CONTACTED", "priority", "LOW");
    assertSameRows(o, w, "leads", List.of(f.lB), "ownerId", m);
    assertSameRows(o, w, "leads", List.of(f.lC), "unassigned", "true");
    assertSameRows(o, w, "leads", List.of(f.lA), "sourceId", String.valueOf(f.website));
    assertSameRows(o, w, "leads", List.of(f.lA), "priority", "HIGH");
    assertSameRows(o, w, "leads", List.of(f.lA), "followUpBefore", "2026-11-15");
    assertSameRows(o, w, "leads", List.of(f.lD), "archived", "true");

    assertSameRows(o, w, "deals", List.of(f.dA, f.dB, f.dC));
    assertSameRows(o, w, "deals", List.of(f.dB), "q", "two");
    assertSameRows(o, w, "deals", List.of(f.dA), "q", "alpha");
    assertSameRows(o, w, "deals", List.of(f.dA, f.dC), "stageId", String.valueOf(f.qualification));
    assertSameRows(o, w, "deals", List.of(f.dB), "status", "WON");
    assertSameRows(o, w, "deals", List.of(f.dB), "ownerId", m);
    assertSameRows(o, w, "deals", List.of(f.dC), "unassigned", "true");
    assertSameRows(o, w, "deals", List.of(f.dB), "companyId", String.valueOf(f.coB));
    assertSameRows(o, w, "deals", List.of(f.dA), "contactId", String.valueOf(f.pA));
    assertSameRows(o, w, "deals", List.of(f.dA), "sourceId", String.valueOf(f.website));
    assertSameRows(o, w, "deals", List.of(f.dB), "closeFrom", "2026-11-15");
    assertSameRows(o, w, "deals", List.of(f.dA), "closeTo", "2026-11-15");
    assertSameRows(o, w, "deals", List.of(f.dD), "archived", "true");

    // The list's own 400 for an owner and unassigned together.
    for (String list : LISTS) {
      export(o, w, list, "unassigned", "true", "ownerId", m)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors.unassigned").exists());
    }
  }

  @Test
  void everyExportHasItsColumns() throws Exception {
    Fixture f = fixture();
    String o = f.owner;
    long w = f.w;

    JsonNode coA = record(o, w, "companies", f.coA);
    assertThat(lines(export(o, w, "companies", "q", "alpha").andReturn()))
        .containsExactly(
            COMPANY_HEADER,
            row(
                ref('C', f.coA),
                "Alpha Co",
                "Software",
                "https://alpha.example",
                "02 123 4567",
                "\"1 Main St, Bangkok\"",
                "250",
                f.ownerEmail,
                "1",
                "1",
                coA.get("createdAt").asText(),
                coA.get("updatedAt").asText(),
                "no"));
    assertThat(lines(export(o, w, "companies", "archived", "true").andReturn()).get(1))
        .startsWith(ref('C', f.coG) + ",Gamma Co,")
        .endsWith(",yes");

    JsonNode lA = record(o, w, "leads", f.lA);
    assertThat(lines(export(o, w, "leads", "q", "lane").andReturn()))
        .containsExactly(
            LEAD_HEADER,
            row(
                ref('L', f.lA),
                "Lia",
                "Lane",
                "Lane Ltd",
                "lia@example.com",
                "",
                "NEW",
                "HIGH",
                "2026-11-01",
                "1250.00",
                "EUR",
                "Website",
                f.ownerEmail,
                lA.get("createdAt").asText(),
                lA.get("updatedAt").asText(),
                "no"));

    JsonNode dA = record(o, w, "deals", f.dA);
    JsonNode dB = record(o, w, "deals", f.dB);
    JsonNode dD = record(o, w, "deals", f.dD);
    assertThat(lines(export(o, w, "deals", "q", "one").andReturn()))
        .containsExactly(
            DEAL_HEADER,
            row(
                ref('D', f.dA),
                "Deal One",
                "1250.00",
                "EUR",
                "Qualification",
                "OPEN",
                "10",
                "2026-11-01",
                "Alpha Co",
                "Ann Archer",
                f.ownerEmail,
                "",
                "",
                dA.get("createdAt").asText(),
                dA.get("updatedAt").asText(),
                "no"));
    assertThat(lines(export(o, w, "deals", "status", "WON").andReturn()))
        .containsExactly(
            DEAL_HEADER,
            row(
                ref('D', f.dB),
                "Deal Two",
                "99.50",
                "EUR",
                "Won",
                "WON",
                "100",
                "2026-12-01",
                "Beta Co",
                "",
                f.managerEmail,
                dB.get("closedAt").asText(),
                "",
                dB.get("createdAt").asText(),
                dB.get("updatedAt").asText(),
                "no"));
    assertThat(lines(export(o, w, "deals", "archived", "true").andReturn()))
        .containsExactly(
            DEAL_HEADER,
            row(
                ref('D', f.dD),
                "Deal Four",
                "1.00",
                "EUR",
                "Lost",
                "LOST",
                "0",
                "",
                "",
                "",
                f.ownerEmail,
                dD.get("closedAt").asText(),
                "\"Budget, timing\"",
                dD.get("createdAt").asText(),
                dD.get("updatedAt").asText(),
                "yes"));
  }

  @Test
  void repGets403() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    // More rows of every list than the test cap (3): the permission is checked before the count,
    // so a rep never learns it from EXPORT_TOO_LARGE.
    for (int i = 0; i < 4; i++) records(rep, w);

    for (String list : LISTS) {
      export(rep, w, list)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertThat(exportEvents(w)).isEmpty();
  }

  @Test
  void otherWorkspaceRowsNeverExported() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String other = tokenFor(uniqueEmail());
    long w2 = createWorkspace(other, "Other");
    List<Long> mine = records(owner, w);
    List<Long> theirs = records(other, w2);

    for (int i = 0; i < LISTS.size(); i++) {
      assertThat(refs(export(owner, w, LISTS.get(i)).andReturn())).containsExactly(mine.get(i));
      assertThat(refs(export(other, w2, LISTS.get(i)).andReturn())).containsExactly(theirs.get(i));
      // A non-member gets the interceptor's 404.
      export(other, w, LISTS.get(i)).andExpect(status().isNotFound());
    }
    // Another workspace's ids as filters match nothing here.
    assertThat(refs(export(owner, w, "contacts", "companyId", "" + theirs.get(0)).andReturn()))
        .isEmpty();
    assertThat(refs(export(owner, w, "deals", "contactId", "" + theirs.get(1)).andReturn()))
        .isEmpty();
  }

  @Test
  void tooManyRowsIs400() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    List<Long> ids = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      ids.add(id(create(owner, w, "companies", Map.of("name", "Row " + i))));
    }

    export(owner, w, "companies")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"))
        .andExpect(jsonPath("$.message").value("Too many rows to export (4); narrow the filters"))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
    assertThat(exportEvents(w)).isEmpty();

    // The cap itself is allowed.
    archive(owner, w, "companies", ids.get(3));
    assertThat(refs(export(owner, w, "companies").andExpect(status().isOk()).andReturn()))
        .containsExactlyElementsOf(ids.subList(0, 3));
  }

  /** A client that accepts anything (a browser's fetch) still gets the JSON {@code ApiError}. */
  @Test
  void tooManyRowsIsJsonForAClientAcceptingAnything() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    for (int i = 0; i < 4; i++) records(owner, w);

    for (String list : LISTS) {
      send(owner, get(W + "/" + list + "/export", w).accept(MediaType.ALL))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"))
          .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
  }

  @Test
  void aClientAcceptingJsonGetsTheCsvAndJsonErrors() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    records(owner, w);

    for (String list : LISTS) {
      send(owner, get(W + "/" + list + "/export", w).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().contentType("text/csv;charset=utf-8"));
    }

    for (int i = 0; i < 3; i++) records(owner, w);
    for (String list : LISTS) {
      send(owner, get(W + "/" + list + "/export", w).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"))
          .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
  }

  @Test
  void exportIsAudited() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    create(owner, w, "contacts", Map.of("firstName", "Ann", "tags", List.of("vip")));
    create(owner, w, "contacts", Map.of("firstName", "Anna", "tags", List.of("vip")));
    create(owner, w, "contacts", Map.of("firstName", "Bob", "tags", List.of("vip")));

    export(manager, w, "contacts", "q", "ann", "tag", "vip").andExpect(status().isOk());
    export(owner, w, "deals").andExpect(status().isOk());
    export(owner, w, "companies", "archived", "true", "unassigned", "false")
        .andExpect(status().isOk());
    create(
        owner,
        w,
        "leads",
        Map.of("firstName", "Lia", "priority", "HIGH", "followUpDate", "2026-11-01"));
    export(
            owner,
            w,
            "leads",
            "status",
            "NEW",
            "status",
            "CONTACTED",
            "priority",
            "HIGH",
            "followUpBefore",
            "2026-11-15")
        .andExpect(status().isOk());

    List<AuditEvent> exports = exportEvents(w);
    assertThat(exports).hasSize(4);
    // The filters are the filter record's fields, not the query parameters (status -> statuses).
    assertThat(exports.get(0).getEntityLabel()).isEqualTo("1 leads exported");
    assertThat(tree(exports.get(0).getChanges()))
        .isEqualTo(
            PLAIN.readTree(
                """
                {"list": {"from": null, "to": "leads"},
                 "filters": {"from": null,
                  "to": {"statuses": ["NEW", "CONTACTED"], "priority": "HIGH",
                         "followUpBefore": "2026-11-15"}}}"""));
    exports = exports.subList(1, exports.size());
    // Newest first.
    AuditEvent contacts = exports.get(2);
    assertThat(contacts.getEntityType()).isEqualTo(AuditEntityType.WORKSPACE);
    assertThat(contacts.getEntityId()).isEqualTo(w);
    assertThat(contacts.getEntityLabel()).isEqualTo("2 contacts exported");
    assertThat(contacts.getActorEmail()).isEqualTo(managerEmail);
    assertThat(tree(contacts.getChanges()))
        .isEqualTo(
            PLAIN.readTree(
                """
                {"list": {"from": null, "to": "contacts"},
                 "filters": {"from": null, "to": {"q": "ann", "tag": "vip"}}}"""));
    // No filters given: none recorded (false is the default, not a filter).
    assertThat(exports.get(1).getEntityLabel()).isEqualTo("0 deals exported");
    assertThat(tree(exports.get(1).getChanges()))
        .isEqualTo(
            PLAIN.readTree(
                """
                {"list": {"from": null, "to": "deals"}, "filters": {"from": null, "to": {}}}"""));
    assertThat(tree(exports.get(0).getChanges().get("filters")))
        .isEqualTo(PLAIN.readTree("{\"from\": null, \"to\": {\"archived\": true}}"));

    // The workspace's history shows it.
    send(
            owner,
            get(W + "/audit-events", w).param("entityType", "WORKSPACE").param("entityId", "" + w))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].action").value("EXPORT"))
        .andExpect(jsonPath("$.content[0].entityLabel").value("1 leads exported"));
  }

  /** Two members and a removed rep with records of every kind (see each line). */
  private Fixture fixture() throws Exception {
    Fixture f = new Fixture();
    f.ownerEmail = uniqueEmail();
    f.owner = tokenFor(f.ownerEmail);
    long w = createWorkspace(f.owner, "Acme");
    f.w = w;
    f.managerEmail = uniqueEmail();
    String manager = tokenFor(f.managerEmail);
    addMember(w, f.managerEmail, WorkspaceRole.MANAGER);
    f.manager = userId(f.managerEmail);
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String o = f.owner;
    f.website = sourceId(o, w, "Website");
    f.referral = sourceId(o, w, "Referral");
    f.qualification = stageId(o, w, "Qualification");

    f.coA =
        id(
            create(
                o,
                w,
                "companies",
                Map.of(
                    "name", "Alpha Co",
                    "industry", "Software",
                    "website", "https://alpha.example",
                    "phone", "02 123 4567",
                    "address", "1 Main St, Bangkok",
                    "employeeCount", 250)));
    f.coB = id(create(manager, w, "companies", Map.of("name", "Beta Co", "industry", "Retail")));
    f.coD = id(create(rep, w, "companies", Map.of("name", "Delta Co", "industry", "Software")));
    f.coG = id(create(o, w, "companies", Map.of("name", "Gamma Co")));
    archive(o, w, "companies", f.coG);

    f.pA =
        id(
            create(
                o,
                w,
                "contacts",
                Map.of(
                    "firstName",
                    "Ann",
                    "lastName",
                    "Archer",
                    "companyId",
                    f.coA,
                    "sourceId",
                    f.website,
                    "tags",
                    List.of("vip"))));
    f.pB =
        id(
            create(
                manager,
                w,
                "contacts",
                Map.of(
                    "firstName",
                    "Ben",
                    "lastName",
                    "Baker",
                    "companyId",
                    f.coB,
                    "sourceId",
                    f.referral)));
    f.pC =
        id(
            create(
                rep,
                w,
                "contacts",
                Map.of("firstName", "Cat", "lastName", "Cole", "tags", List.of("vip"))));
    f.pD = id(create(o, w, "contacts", Map.of("firstName", "Dan", "lastName", "Dorn")));
    archive(o, w, "contacts", f.pD);

    f.lA =
        id(
            create(
                o,
                w,
                "leads",
                Map.of(
                    "firstName", "Lia",
                    "lastName", "Lane",
                    "companyName", "Lane Ltd",
                    "email", "lia@example.com",
                    "priority", "HIGH",
                    "followUpDate", "2026-11-01",
                    "sourceId", f.website,
                    "estimatedValue", "1250")));
    f.lB =
        id(
            create(
                manager,
                w,
                "leads",
                Map.of(
                    "firstName", "Max",
                    "lastName", "Moss",
                    "priority", "LOW",
                    "followUpDate", "2026-12-01")));
    send(o, post(W + "/leads/{id}/status", w, f.lB), Map.of("status", "CONTACTED", "version", 0))
        .andExpect(status().isOk());
    f.lC = id(create(rep, w, "leads", Map.of("firstName", "Noa", "lastName", "Nash")));
    f.lD = id(create(o, w, "leads", Map.of("firstName", "Oli")));
    archive(o, w, "leads", f.lD);

    f.dA =
        id(
            create(
                o,
                w,
                "deals",
                Map.of(
                    "name",
                    "Deal One",
                    "amount",
                    "1250",
                    "companyId",
                    f.coA,
                    "contactId",
                    f.pA,
                    "sourceId",
                    f.website,
                    "expectedCloseDate",
                    "2026-11-01")));
    f.dB =
        id(
            create(
                manager,
                w,
                "deals",
                Map.of(
                    "name",
                    "Deal Two",
                    "amount",
                    "99.5",
                    "companyId",
                    f.coB,
                    "expectedCloseDate",
                    "2026-12-01")));
    send(
            o,
            post(W + "/deals/{id}/move", w, f.dB),
            Map.of("stageId", stageId(o, w, "Won"), "version", 0))
        .andExpect(status().isOk());
    f.dC = id(create(rep, w, "deals", Map.of("name", "Deal Three", "amount", "1")));
    f.dD = id(create(o, w, "deals", Map.of("name", "Deal Four", "amount", "1")));
    send(
            o,
            post(W + "/deals/{id}/move", w, f.dD),
            Map.of("stageId", stageId(o, w, "Lost"), "version", 0, "lostReason", "Budget, timing"))
        .andExpect(status().isOk());
    archive(o, w, "deals", f.dD);

    // Removing the rep leaves their records unassigned.
    send(o, delete(W + "/members/{userId}", w, userId(repEmail))).andExpect(status().isNoContent());
    return f;
  }

  static final class Fixture {
    String owner;
    String ownerEmail;
    String managerEmail;
    long w;
    long manager;
    long website;
    long referral;
    long qualification;
    long coA;
    long coB;
    long coD;
    long coG;
    long pA;
    long pB;
    long pC;
    long pD;
    long lA;
    long lB;
    long lC;
    long lD;
    long dA;
    long dB;
    long dC;
    long dD;
  }

  /** The export and the list with the same filters give {@code expected}, in the same order. */
  private void assertSameRows(
      String token, long w, String list, List<Long> expected, String... params) throws Exception {
    MockHttpServletRequestBuilder listed = get(W + "/" + list, w).param("size", "100");
    for (int i = 0; i < params.length; i += 2) listed.param(params[i], params[i + 1]);
    List<Long> listIds = new ArrayList<>();
    body(send(token, listed).andExpect(status().isOk()))
        .get("content")
        .forEach(r -> listIds.add(r.get("id").asLong()));

    List<Long> exported =
        refs(export(token, w, list, params).andExpect(status().isOk()).andReturn());

    String what = list + " " + Arrays.toString(params);
    assertThat(listIds).as(what).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(exported).as(what).isEqualTo(listIds);
  }

  /** One company, contact, lead and deal, in that order. */
  private List<Long> records(String token, long w) throws Exception {
    long company = id(create(token, w, "companies", Map.of("name", "Co")));
    long contact = id(create(token, w, "contacts", Map.of("firstName", "Pat")));
    long lead = id(create(token, w, "leads", Map.of("firstName", "Lee")));
    long deal = id(create(token, w, "deals", Map.of("name", "Deal", "amount", "1")));
    return List.of(company, contact, lead, deal);
  }

  private ResultActions export(String token, long w, String list, String... params)
      throws Exception {
    MockHttpServletRequestBuilder request = get(W + "/" + list + "/export", w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  /** The lines of a CSV without line breaks inside values: the BOM and the last CRLF checked. */
  private static List<String> lines(MvcResult result) {
    String csv = new String(result.getResponse().getContentAsByteArray(), UTF_8);
    assertThat(csv).startsWith("﻿").endsWith("\r\n");
    return List.of(csv.substring(1, csv.length() - 2).split("\r\n", -1));
  }

  /** The ids of the exported rows, from their references. */
  private static List<Long> refs(MvcResult result) {
    List<String> lines = lines(result);
    return lines.subList(1, lines.size()).stream()
        .map(l -> Long.parseLong(l.substring(2, l.indexOf(','))))
        .toList();
  }

  private static String ref(char prefix, long id) {
    return String.format(Locale.ROOT, "%c-%04d", prefix, id);
  }

  private static String row(String... cells) {
    return String.join(",", cells);
  }

  private List<AuditEvent> exportEvents(long w) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getAction() == AuditAction.EXPORT)
        .toList();
  }

  private JsonNode create(String token, long w, String list, Map<String, Object> body)
      throws Exception {
    return body(send(token, post(W + "/" + list, w), body).andExpect(status().isCreated()));
  }

  private JsonNode record(String token, long w, String list, long id) throws Exception {
    return body(send(token, get(W + "/" + list + "/{id}", w, id)).andExpect(status().isOk()));
  }

  private void archive(String token, long w, String list, long id) throws Exception {
    send(token, post(W + "/" + list + "/{id}/archive", w, id)).andExpect(status().isOk());
  }

  /** As stored: a plain mapper keeps the nulls that the app's mapper leaves out. */
  private static JsonNode tree(Object value) {
    return PLAIN.valueToTree(value);
  }

  private static long id(JsonNode created) {
    return created.get("id").asLong();
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long sourceId(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(W + "/lead-sources", w)))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No source " + name);
  }

  private long stageId(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(W + "/pipeline-stages", w)))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No stage " + name);
  }
}
