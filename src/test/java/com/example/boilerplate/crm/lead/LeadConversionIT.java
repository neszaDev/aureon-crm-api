package com.example.boilerplate.crm.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditEvent;
import com.example.boilerplate.audit.AuditEventRepository;
import com.example.boilerplate.crm.activity.ActivityRepository;
import com.example.boilerplate.crm.task.TaskRepository;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class LeadConversionIT extends WorkspaceTestSupport {
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";
  static final String LEAD = LEADS + "/{id}";
  static final String CONVERT = LEAD + "/convert";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String SOURCES = "/api/v1/workspaces/{workspaceId}/lead-sources";
  static final String ACTIVITIES = "/api/v1/workspaces/{workspaceId}/activities";
  static final String TASKS = "/api/v1/workspaces/{workspaceId}/tasks";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired ActivityRepository activities;
  @Autowired TaskRepository tasks;

  /** A manager converts a rep's lead: the new records belong to the rep, the lead's owner. */
  @Test
  void convertCreatesCompanyContactAndDeal() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long repId = userId(repEmail);
    JsonNode source = body(send(rep, get(SOURCES, w))).get(0);
    Map<String, Object> input = new HashMap<>();
    input.put("firstName", "Ada");
    input.put("lastName", "Lovelace");
    input.put("companyName", "Analytical Engines");
    input.put("email", "Ada@Example.com");
    input.put("phone", "+44 20 7946 0000");
    input.put("jobTitle", "Engineer");
    input.put("sourceId", source.get("id").asLong());
    long lead = qualifiedLead(rep, w, input);
    long proposal = stages(owner, w).get("Proposal");

    JsonNode result =
        body(
            convert(
                    owner,
                    w,
                    lead,
                    conversion(
                        null, true, null, deal("Engines", "5000", "usd", proposal, "2026-12-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadId").value(lead))
                .andExpect(jsonPath("$.company.name").value("Analytical Engines"))
                .andExpect(jsonPath("$.contact.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.deal.name").value("Engines"))
                .andExpect(jsonPath("$.alreadyConverted").value(false)));
    long company = result.get("company").get("id").asLong();
    long contact = result.get("contact").get("id").asLong();
    long deal = result.get("deal").get("id").asLong();

    send(owner, get(LEAD, w, lead))
        .andExpect(jsonPath("$.status").value("CONVERTED"))
        .andExpect(jsonPath("$.convertedAt").exists())
        .andExpect(jsonPath("$.convertedCompany.id").value(company))
        .andExpect(jsonPath("$.convertedCompany.name").value("Analytical Engines"))
        .andExpect(jsonPath("$.convertedContact.id").value(contact))
        .andExpect(jsonPath("$.convertedContact.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.convertedDeal.id").value(deal))
        .andExpect(jsonPath("$.convertedDeal.name").value("Engines"))
        .andExpect(jsonPath("$.owner.userId").value(repId))
        .andExpect(jsonPath("$.version").value(2));
    send(owner, get(LEADS, w))
        .andExpect(jsonPath("$.content[0].convertedDeal.id").value(deal))
        .andExpect(jsonPath("$.content[0].convertedDeal.name").value("Engines"));
    send(owner, get(COMPANIES + "/{id}", w, company))
        .andExpect(jsonPath("$.owner.userId").value(repId))
        .andExpect(jsonPath("$.contactCount").value(1))
        .andExpect(jsonPath("$.openDealCount").value(1));
    send(owner, get(CONTACTS + "/{id}", w, contact))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.lastName").value("Lovelace"))
        .andExpect(jsonPath("$.email").value("ada@example.com"))
        .andExpect(jsonPath("$.phone").value("+44 20 7946 0000"))
        .andExpect(jsonPath("$.jobTitle").value("Engineer"))
        .andExpect(jsonPath("$.company.id").value(company))
        .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
        .andExpect(jsonPath("$.owner.userId").value(repId));
    send(owner, get(DEALS + "/{id}", w, deal))
        .andExpect(jsonPath("$.stage.id").value(proposal))
        .andExpect(jsonPath("$.amount").value("5000.00"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.expectedCloseDate").value("2026-12-01"))
        .andExpect(jsonPath("$.company.id").value(company))
        .andExpect(jsonPath("$.contact.id").value(contact))
        .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
        .andExpect(jsonPath("$.lead.id").value(lead))
        .andExpect(jsonPath("$.lead.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.owner.userId").value(repId));
    // The deal's first history entry, written by the one who converted.
    send(owner, get(DEALS + "/{id}/history", w, deal))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].fromStage").doesNotExist())
        .andExpect(jsonPath("$[0].toStage.id").value(proposal));

    List<AuditEvent> trail = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w);
    assertThat(trail)
        .filteredOn(
            e ->
                e.getAction() != AuditAction.STATUS_CHANGE
                    && e.getEntityType() != AuditEntityType.WORKSPACE)
        .extracting(e -> e.getAction() + " " + e.getEntityType() + " " + e.getEntityId())
        .containsExactly(
            "CONVERT LEAD " + lead,
            "CREATE DEAL " + deal,
            "CREATE CONTACT " + contact,
            "CREATE COMPANY " + company,
            "CREATE LEAD " + lead);
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Ada Lovelace");
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys(
            "status", "convertedAt", "convertedCompany", "convertedContact", "convertedDeal");
  }

  /** Nobody owns the lead: the records go to the one who converts it. */
  @Test
  void unassignedLeadGivesTheCallerTheRecords() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada", "companyName", "Engines"));
    jdbc.update("UPDATE leads SET owner_id = NULL WHERE id = ?", lead);

    JsonNode result =
        body(
            convert(token, w, lead, conversion(null, true, null, deal("D", "1", null, null, null)))
                .andExpect(status().isOk()));

    for (String path : List.of(COMPANIES, CONTACTS, DEALS)) {
      String key = path.equals(COMPANIES) ? "company" : path.equals(CONTACTS) ? "contact" : "deal";
      send(token, get(path + "/{id}", w, result.get(key).get("id").asLong()))
          .andExpect(jsonPath("$.owner.userId").value(userId(email)));
    }
    // The workspace's currency and its first open stage, as for any new deal.
    send(token, get(DEALS + "/{id}", w, result.get("deal").get("id").asLong()))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.stage.name").value("Qualification"));
  }

  @Test
  void convertLinksExistingCompanyAndContact() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme")));
    long ada = id(send(token, post(CONTACTS, w), Map.of("firstName", "Ada", "lastName", "L")));
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada", "companyName", "Other Co"));
    long companies = count("companies", w);
    long contacts = count("contacts", w);

    // createCompany is ignored when a company is given.
    convert(token, w, lead, conversion(acme, true, ada, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company.id").value(acme))
        .andExpect(jsonPath("$.company.name").value("Acme"))
        .andExpect(jsonPath("$.contact.id").value(ada))
        .andExpect(jsonPath("$.contact.fullName").value("Ada L"))
        .andExpect(jsonPath("$.deal").doesNotExist())
        .andExpect(jsonPath("$.alreadyConverted").value(false));

    assertThat(count("companies", w)).isEqualTo(companies);
    assertThat(count("contacts", w)).isEqualTo(contacts);
    assertThat(count("deals", w)).isZero();
    send(token, get(LEAD, w, lead))
        .andExpect(jsonPath("$.convertedCompany.id").value(acme))
        .andExpect(jsonPath("$.convertedContact.id").value(ada))
        .andExpect(jsonPath("$.convertedDeal").doesNotExist());
    // The linked contact keeps its own company (none).
    send(token, get(CONTACTS + "/{id}", w, ada)).andExpect(jsonPath("$.company").doesNotExist());
    assertThat(trail(w))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.CONVERT, AuditAction.STATUS_CHANGE, AuditAction.CREATE);
  }

  /** Without a company to link or a name to create one from, the contact stands alone. */
  @Test
  void noCompanyUnlessAskedAndNamed() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long named = qualifiedLead(token, w, Map.of("firstName", "Ada", "companyName", "Engines"));
    long unnamed = qualifiedLead(token, w, Map.of("firstName", "Bea"));

    convert(token, w, named, conversion(null, false, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact.fullName").value("Ada"));
    convert(token, w, unnamed, conversion(null, true, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact.fullName").value("Bea"));
    assertThat(count("companies", w)).isZero();
  }

  @Test
  void invalidLinksAre400AndWriteNothing() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirCompany = id(send(b, post(COMPANIES, wb), Map.of("name", "B's")));
    long theirContact = id(send(b, post(CONTACTS, wb), Map.of("firstName", "B")));
    long archived = id(send(a, post(COMPANIES, wa), Map.of("name", "Old")));
    send(a, post(COMPANIES + "/{id}/archive", wa, archived)).andExpect(status().isOk());
    long archivedContact = id(send(a, post(CONTACTS, wa), Map.of("firstName", "Old")));
    send(a, post(CONTACTS + "/{id}/archive", wa, archivedContact)).andExpect(status().isOk());
    long lead = qualifiedLead(a, wa, Map.of("firstName", "Ada", "companyName", "Engines"));

    invalid(a, wa, lead, conversion(theirCompany, false, null, null), "companyId");
    invalid(a, wa, lead, conversion(archived, false, null, null), "companyId");
    invalid(a, wa, lead, conversion(null, true, theirContact, null), "contactId");
    send(a, post(CONVERT, wa, lead), conversion(null, true, archivedContact, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.contactId").value("is archived"));
    invalid(
        a, wa, lead, conversion(null, true, null, deal(" ", "1", null, null, null)), "deal.name");
    invalid(
        a,
        wa,
        lead,
        conversion(null, true, null, deal("D", null, null, null, null)),
        "deal.amount");
    invalid(
        a,
        wa,
        lead,
        conversion(null, true, null, deal("D", "-1", null, null, null)),
        "deal.amount");
    invalid(
        a,
        wa,
        lead,
        conversion(null, true, null, deal("D", "1", "XYZ", null, null)),
        "deal.currency");
    Map<String, Object> noFlag = new HashMap<>();
    noFlag.put("companyId", null);
    invalid(a, wa, lead, noFlag, "createCompany");

    assertThat(count("companies", wa)).isEqualTo(1);
    // Only the archived contact made above.
    assertThat(count("contacts", wa)).isEqualTo(1);
    send(a, get(LEAD, wa, lead))
        .andExpect(jsonPath("$.status").value("QUALIFIED"))
        .andExpect(jsonPath("$.version").value(1));
  }

  /** B's token and workspace with A's lead id: not found, and A's lead stays as it was. */
  @Test
  void leadOfAnotherWorkspaceIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirLead = qualifiedLead(a, wa, Map.of("firstName", "Ada"));

    convert(b, wb, theirLead, conversion(null, false, null, null)).andExpect(status().isNotFound());

    send(a, get(LEAD, wa, theirLead)).andExpect(jsonPath("$.status").value("QUALIFIED"));
    assertThat(count("contacts", wa)).isZero();
    assertThat(count("contacts", wb)).isZero();
  }

  @Test
  void retryReturnsSameResult() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada", "companyName", "Engines"));
    Map<String, Object> request = conversion(null, true, null, deal("D", "10", null, null, null));
    JsonNode first = body(convert(token, w, lead, request).andExpect(status().isOk()));
    int auditCount = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).size();

    // The same request, then a different one: both get the first result back.
    for (Map<String, Object> retry :
        List.of(request, conversion(null, false, null, deal("Other", "99", null, null, null)))) {
      JsonNode again = body(convert(token, w, lead, retry).andExpect(status().isOk()));
      assertThat(again.get("alreadyConverted").asBoolean()).isTrue();
      assertThat(again.get("leadId")).isEqualTo(first.get("leadId"));
      assertThat(again.get("company")).isEqualTo(first.get("company"));
      assertThat(again.get("contact")).isEqualTo(first.get("contact"));
      assertThat(again.get("deal")).isEqualTo(first.get("deal"));
    }

    assertThat(count("companies", w)).isEqualTo(1);
    assertThat(count("contacts", w)).isEqualTo(1);
    assertThat(count("deals", w)).isEqualTo(1);
    assertThat(events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w)).hasSize(auditCount);
    send(token, get(LEAD, w, lead)).andExpect(jsonPath("$.version").value(2));

    // An archived target still shows up in the stored result.
    long company = first.get("company").get("id").asLong();
    send(token, post(COMPANIES + "/{id}/archive", w, company)).andExpect(status().isOk());
    convert(token, w, lead, request)
        .andExpect(jsonPath("$.company.id").value(company))
        .andExpect(jsonPath("$.alreadyConverted").value(true));
  }

  /**
   * Two conversions of one lead at the same moment: the row lock makes the second wait and then
   * find the lead converted. Several rounds, so a lucky ordering can't hide a race.
   */
  @Test
  void concurrentConvertCreatesOnce() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        String email = "ada-" + round + "@example.com";
        long lead =
            qualifiedLead(
                token,
                w,
                Map.of("firstName", "Ada", "companyName", "Engines " + round, "email", email));
        Map<String, Object> request =
            conversion(null, true, null, deal("D" + round, "10", null, null, null));
        CountDownLatch start = new CountDownLatch(1);
        Callable<ResultActions> convertIt =
            () -> {
              start.await();
              return convert(token, w, lead, request);
            };
        List<Future<ResultActions>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) futures.add(pool.submit(convertIt));
        start.countDown();
        List<JsonNode> results = new ArrayList<>();
        for (Future<ResultActions> f : futures) {
          results.add(body(f.get(30, TimeUnit.SECONDS).andExpect(status().isOk())));
        }

        assertThat(results)
            .extracting(r -> r.get("alreadyConverted").asBoolean())
            .containsExactlyInAnyOrder(false, true);
        assertThat(results.get(0).get("contact")).isEqualTo(results.get(1).get("contact"));
        assertThat(results.get(0).get("deal")).isEqualTo(results.get(1).get("deal"));
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM contacts WHERE workspace_id = ? AND email = ?",
                    Long.class,
                    w,
                    email))
            .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM companies WHERE workspace_id = ? AND name = ?",
                    Long.class,
                    w,
                    "Engines " + round))
            .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM deals WHERE lead_id = ?", Long.class, lead))
            .isEqualTo(1L);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void notQualifiedIs409() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long fresh = create(token, w, Map.of("firstName", "Ada", "companyName", "Engines"));
    long unqualified = create(token, w, Map.of("firstName", "Bea"));
    setStatus(token, w, unqualified, "UNQUALIFIED", "Too small", 0).andExpect(status().isOk());

    for (long lead : List.of(fresh, unqualified)) {
      convert(token, w, lead, conversion(null, true, null, deal("D", "1", null, null, null)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("Only qualified leads can be converted"));
    }
    assertThat(count("companies", w)).isZero();
    assertThat(count("contacts", w)).isZero();
    assertThat(count("deals", w)).isZero();
    send(token, post(CONVERT, w, 999_999_999L), conversion(null, true, null, null))
        .andExpect(status().isNotFound());
  }

  /** A deal the deal rules refuse undoes the company and contact created before it. */
  @Test
  void failedConversionRollsBack() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long lead = qualifiedLead(a, wa, Map.of("firstName", "Ada", "companyName", "Engines"));
    int auditCount = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(wa).size();

    invalid(
        a,
        wa,
        lead,
        conversion(null, true, null, deal("D", "1", null, stages(b, wb).get("Proposal"), null)),
        "deal.stageId");
    // A closed stage of the lead's own workspace is refused too.
    invalid(
        a,
        wa,
        lead,
        conversion(null, true, null, deal("D", "1", null, stages(a, wa).get("Won"), null)),
        "deal.stageId");

    assertThat(count("companies", wa)).isZero();
    assertThat(count("contacts", wa)).isZero();
    assertThat(count("deals", wa)).isZero();
    assertThat(events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(wa)).hasSize(auditCount);
    send(a, get(LEAD, wa, lead))
        .andExpect(jsonPath("$.status").value("QUALIFIED"))
        .andExpect(jsonPath("$.convertedAt").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void repCannotConvertOthersLead() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String otherEmail = uniqueEmail();
    String other = tokenFor(otherEmail);
    addMember(w, otherEmail, WorkspaceRole.SALES_REP);
    long lead = qualifiedLead(rep, w, Map.of("firstName", "Ada", "companyName", "Engines"));

    convert(other, w, lead, conversion(null, true, null, null)).andExpect(status().isForbidden());
    assertThat(count("contacts", w)).isZero();
    send(rep, get(LEAD, w, lead)).andExpect(jsonPath("$.status").value("QUALIFIED"));

    convert(rep, w, lead, conversion(null, true, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alreadyConverted").value(false));
    // Not even a retry for someone who couldn't have converted it.
    convert(other, w, lead, conversion(null, true, null, null)).andExpect(status().isForbidden());
  }

  /** Archiving hides a lead, it doesn't lock it: an archived QUALIFIED lead converts. */
  @Test
  void archivedQualifiedLeadConverts() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada"));
    send(token, post(LEAD + "/archive", w, lead))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));

    convert(token, w, lead, conversion(null, false, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alreadyConverted").value(false));
    send(token, get(LEAD, w, lead))
        .andExpect(jsonPath("$.status").value("CONVERTED"))
        .andExpect(jsonPath("$.archived").value(true));
    assertThat(count("contacts", w)).isEqualTo(1);
  }

  @Test
  void convertedLeadIsReadOnly() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada"));
    convert(token, w, lead, conversion(null, false, null, null)).andExpect(status().isOk());

    Map<String, Object> edit = new HashMap<>();
    edit.put("firstName", "Edited");
    edit.put("ownerId", userId(email));
    edit.put("version", 2);
    send(token, put(LEAD, w, lead), edit)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Converted leads can't be edited"));
    setStatus(token, w, lead, "QUALIFIED", null, 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Converted leads can't change status"));
    send(token, get(LEAD, w, lead))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.status").value("CONVERTED"));
  }

  /**
   * The lead's activities and tasks join the contact, company and deal it became, so the contact's
   * timeline and tasks show them. A link they already have stays. A retry links nothing again.
   */
  @Test
  void conversionCarriesTheLeadsHistory() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long lead = qualifiedLead(token, w, Map.of("firstName", "Ada", "companyName", "Engines"));
    long call =
        id(
            send(
                token,
                post(ACTIVITIES, w),
                Map.of("type", "CALL", "subject", "First call", "leadId", lead)));
    long grace = id(send(token, post(CONTACTS, w), Map.of("firstName", "Grace")));
    long meeting =
        id(
            send(
                token,
                post(ACTIVITIES, w),
                Map.of(
                    "type",
                    "MEETING",
                    "subject",
                    "With Grace",
                    "leadId",
                    lead,
                    "contactId",
                    grace)));
    long task = id(send(token, post(TASKS, w), Map.of("title", "Send the offer", "leadId", lead)));

    JsonNode result =
        body(
            convert(token, w, lead, conversion(null, true, null, deal("D", "10", null, null, null)))
                .andExpect(status().isOk()));
    long company = result.get("company").get("id").asLong();
    long contact = result.get("contact").get("id").asLong();
    long deal = result.get("deal").get("id").asLong();

    send(token, get(ACTIVITIES, w).param("contactId", String.valueOf(contact)))
        .andExpect(jsonPath("$.content[*].id", contains((int) call)));
    send(token, get(ACTIVITIES + "/{id}", w, call))
        .andExpect(jsonPath("$.lead.id").value(lead))
        .andExpect(jsonPath("$.contact.id").value(contact))
        .andExpect(jsonPath("$.company.id").value(company))
        .andExpect(jsonPath("$.deal.id").value(deal))
        .andExpect(jsonPath("$.version").value(1));
    send(token, get(ACTIVITIES + "/{id}", w, meeting))
        .andExpect(jsonPath("$.contact.id").value(grace))
        .andExpect(jsonPath("$.deal.id").value(deal));
    send(token, get(TASKS, w).param("contactId", String.valueOf(contact)))
        .andExpect(jsonPath("$.content[*].id", contains((int) task)))
        .andExpect(jsonPath("$.content[0].company.id").value(company))
        .andExpect(jsonPath("$.content[0].deal.id").value(deal))
        .andExpect(jsonPath("$.content[0].version").value(1));
    assertThat(trail(w).get(0).getChanges())
        .containsEntry("linkedActivities", linked(2))
        .containsEntry("linkedTasks", linked(1));

    // Logged after the conversion: a retry returns the first result and links nothing.
    long later =
        id(
            send(
                token,
                post(ACTIVITIES, w),
                Map.of("type", "NOTE", "subject", "Later", "leadId", lead)));
    convert(token, w, lead, conversion(null, true, null, null))
        .andExpect(jsonPath("$.alreadyConverted").value(true));
    send(token, get(ACTIVITIES + "/{id}", w, later))
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
    send(token, get(ACTIVITIES + "/{id}", w, call)).andExpect(jsonPath("$.version").value(1));
  }

  /**
   * Onto an existing contact without a company in the request: the lead's history gets the
   * contact's company, as an activity logged on that contact would.
   */
  @Test
  void historyOfAnExistingContactGetsItsCompany() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long initech = id(send(token, post(COMPANIES, w), Map.of("name", "Initech")));
    long contact =
        id(send(token, post(CONTACTS, w), Map.of("firstName", "Grace", "companyId", initech)));
    long lead = qualifiedLead(token, w, Map.of("firstName", "Grace", "companyName", "Ignored"));
    long call =
        id(
            send(
                token,
                post(ACTIVITIES, w),
                Map.of("type", "CALL", "subject", "Intro", "leadId", lead)));
    long task = id(send(token, post(TASKS, w), Map.of("title", "Follow up", "leadId", lead)));

    convert(token, w, lead, conversion(null, false, contact, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").doesNotExist());

    send(token, get(ACTIVITIES + "/{id}", w, call))
        .andExpect(jsonPath("$.contact.id").value(contact))
        .andExpect(jsonPath("$.company.id").value(initech));
    send(token, get(TASKS + "/{id}", w, task))
        .andExpect(jsonPath("$.contact.id").value(contact))
        .andExpect(jsonPath("$.company.id").value(initech));
  }

  /**
   * The bulk updates bind the workspace: another one's rows stay untouched whatever the lead id.
   */
  @Test
  void linkingTheHistoryStaysInTheWorkspace() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    long lead = create(a, wa, Map.of("firstName", "Ada"));
    long call =
        id(
            send(
                a,
                post(ACTIVITIES, wa),
                Map.of("type", "CALL", "subject", "Call", "leadId", lead)));
    long task = id(send(a, post(TASKS, wa), Map.of("title", "Follow up", "leadId", lead)));
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long theirContact = id(send(b, post(CONTACTS, wb), Map.of("firstName", "B")));
    Membership inB = new Membership(wb, userId(bEmail), bEmail, WorkspaceRole.OWNER);
    Instant now = Instant.now();

    assertThat(
            WorkspaceContext.runAs(
                inB, () -> activities.linkConvertedLead(wb, lead, theirContact, null, null, now)))
        .isZero();
    assertThat(
            WorkspaceContext.runAs(
                inB, () -> tasks.linkConvertedLead(wb, lead, theirContact, null, null, now)))
        .isZero();

    send(a, get(ACTIVITIES + "/{id}", wa, call))
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
    send(a, get(TASKS + "/{id}", wa, task))
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
  }

  private static Map<String, Object> linked(int count) {
    Map<String, Object> change = new HashMap<>();
    change.put("from", null);
    change.put("to", count);
    return change;
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(LEADS, w), input));
  }

  /** A new lead moved to QUALIFIED (version 1). */
  private long qualifiedLead(String token, long w, Map<String, ?> input) throws Exception {
    long id = create(token, w, input);
    setStatus(token, w, id, "QUALIFIED", null, 0).andExpect(status().isOk());
    return id;
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
  }

  private ResultActions setStatus(
      String token, long w, long id, String status, String reason, long v) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("status", status);
    body.put("reason", reason);
    body.put("version", v);
    return send(token, post(LEAD + "/status", w, id), body);
  }

  private ResultActions convert(String token, long w, long lead, Map<String, ?> body)
      throws Exception {
    return send(token, post(CONVERT, w, lead), body);
  }

  private void invalid(String token, long w, long lead, Map<String, ?> body, String field)
      throws Exception {
    convert(token, w, lead, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['" + field + "']").exists());
  }

  private static Map<String, Object> conversion(
      Long companyId, boolean createCompany, Long contactId, Map<String, Object> deal) {
    Map<String, Object> body = new HashMap<>();
    body.put("companyId", companyId);
    body.put("createCompany", createCompany);
    body.put("contactId", contactId);
    body.put("deal", deal);
    return body;
  }

  private static Map<String, Object> deal(
      String name, String amount, String currency, Long stageId, String expectedCloseDate) {
    Map<String, Object> deal = new HashMap<>();
    deal.put("name", name);
    deal.put("amount", amount);
    deal.put("currency", currency);
    deal.put("stageId", stageId);
    deal.put("expectedCloseDate", expectedCloseDate);
    return deal;
  }

  private Map<String, Long> stages(String token, long w) throws Exception {
    Map<String, Long> ids = new HashMap<>();
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      ids.put(s.get("name").asText(), s.get("id").asLong());
    }
    return ids;
  }

  /** Rows of {@code table} in the workspace, archived ones included. */
  private long count(String table, long w) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE workspace_id = ?", Long.class, w);
  }

  /** The workspace's lead events, newest first. */
  private List<AuditEvent> trail(long w) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.LEAD)
        .toList();
  }
}
