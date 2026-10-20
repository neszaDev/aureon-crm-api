package com.example.boilerplate.crm.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.TestClock;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class DashboardIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";
  static final String DASHBOARD = W + "/dashboard";
  static final List<String> RECORD_TYPES =
      List.of("COMPANY", "CONTACT", "LEAD", "DEAL", "TASK", "ACTIVITY");

  @Autowired JdbcTemplate jdbc;
  @Autowired TestClock clock;

  @AfterEach
  void unpinTheClock() {
    clock.unpin();
  }

  @Test
  void emptyWorkspaceIsAllZeros() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Empty");
    ZoneId paris = ZoneId.of("Europe/Paris");

    LocalDate before = LocalDate.now(paris);
    ResultActions result = dashboard(token, w);
    LocalDate after = LocalDate.now(paris);

    assertAllZeros(result.andExpect(status().isOk()));
    // The last 30 days up to today in the workspace's zone (the request may cross midnight).
    JsonNode period = body(result).get("period");
    LocalDate to = LocalDate.parse(period.get("to").asText());
    assertThat(to).isIn(before, after);
    assertThat(period.get("from").asText()).isEqualTo(to.minusDays(29).toString());
  }

  @Test
  void valuesMatchStoredData() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long repId = users.findByEmail(repEmail).orElseThrow().getId();
    Map<String, Long> stage = stages(owner, w);

    // 2 companies, one archived; 3 contacts.
    long acme = create(owner, w, "/companies", Map.of("name", "Acme"));
    long gone = create(owner, w, "/companies", Map.of("name", "Gone"));
    long ann = create(owner, w, "/contacts", Map.of("firstName", "Ann"));
    create(owner, w, "/contacts", Map.of("firstName", "Bo"));
    create(owner, w, "/contacts", Map.of("firstName", "Cy"));

    // Leads NEW, QUALIFIED, CONVERTED (onto Ann: no new contact), UNQUALIFIED; an archived NEW one.
    create(owner, w, "/leads", Map.of("firstName", "New"));
    long qualified = create(owner, w, "/leads", Map.of("firstName", "Qualified"));
    leadStatus(owner, w, qualified, "QUALIFIED", 0);
    long converted = create(owner, w, "/leads", Map.of("firstName", "Converted"));
    leadStatus(owner, w, converted, "QUALIFIED", 0);
    send(
            owner,
            post(W + "/leads/{id}/convert", w, converted),
            Map.of("createCompany", false, "contactId", ann))
        .andExpect(status().isOk());
    long unqualified = create(owner, w, "/leads", Map.of("firstName", "Unqualified"));
    leadStatus(owner, w, unqualified, "UNQUALIFIED", 0);
    long archivedLead = create(owner, w, "/leads", Map.of("firstName", "Archived"));
    send(owner, post(W + "/leads/{id}/archive", w, archivedLead)).andExpect(status().isOk());

    // Open deals in two currencies; one won and one lost today; an archived open deal.
    deal(owner, w, "USD 1000.00", "1000.00", "USD", stage.get("Proposal"), null);
    deal(owner, w, "USD 500.00", "500.00", "USD", stage.get("Qualification"), 20);
    deal(owner, w, "EUR 300.00", "300.00", "EUR", stage.get("Negotiation"), null);
    long won = deal(owner, w, "Won", "700.00", "USD", stage.get("Proposal"), null);
    move(owner, w, won, stage.get("Won"), null);
    long lost = deal(owner, w, "Lost", "50.00", "EUR", stage.get("Proposal"), null);
    move(owner, w, lost, stage.get("Lost"), "Budget");
    long archivedDeal = deal(owner, w, "Archived", "9999.00", "USD", stage.get("Proposal"), null);
    send(owner, post(W + "/deals/{id}/archive", w, archivedDeal)).andExpect(status().isOk());

    // The owner's overdue and upcoming tasks, the rep's overdue one; a done one and an undated
    // one count nowhere.
    task(owner, w, "Owner overdue", hours(-2), null);
    task(owner, w, "Owner upcoming", hours(2), null);
    task(owner, w, "Rep overdue", hours(-5), repId);
    long done = task(owner, w, "Done overdue", hours(-3), null);
    send(owner, post(W + "/tasks/{id}/complete", w, done), Map.of("version", 0))
        .andExpect(status().isOk());
    task(owner, w, "Undated", null, null);

    // 9 activities, created oldest occurredAt last: the 8 newest by occurredAt are A1..A8.
    for (int i = 1; i <= 9; i++) {
      create(
          owner,
          w,
          "/activities",
          Map.of(
              "type",
              "CALL",
              "subject",
              "A" + i,
              "occurredAt",
              hours(-i).toString(),
              "companyId",
              acme));
    }
    // The newest record change, then an export: a WORKSPACE event, not a record change.
    send(owner, post(W + "/companies/{id}/archive", w, gone)).andExpect(status().isOk());
    send(owner, get(W + "/companies/export", w)).andExpect(status().isOk());

    dashboard(owner, w)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts.companies").value(1))
        .andExpect(jsonPath("$.counts.contacts").value(3))
        .andExpect(jsonPath("$.counts.openLeads").value(2))
        .andExpect(jsonPath("$.counts.openDeals").value(3))
        .andExpect(jsonPath("$.counts.overdueTasks").value(2))
        // Per currency, by code; weighted with the deal's override, else the stage's probability.
        .andExpect(jsonPath("$.openPipeline[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.openPipeline[*].amount", contains("300.00", "1500.00")))
        .andExpect(jsonPath("$.openPipeline[*].weightedAmount", contains("225.00", "600.00")))
        .andExpect(jsonPath("$.won.count").value(1))
        .andExpect(jsonPath("$.won.totals[*].currency", contains("USD")))
        .andExpect(jsonPath("$.won.totals[*].amount", contains("700.00")))
        .andExpect(jsonPath("$.lost.count").value(1))
        // Open stages in order, the empty one too.
        .andExpect(
            jsonPath(
                "$.byStage[*].stage.name",
                contains("Qualification", "Needs analysis", "Proposal", "Negotiation")))
        .andExpect(jsonPath("$.byStage[*].count", contains(1, 0, 1, 1)))
        .andExpect(jsonPath("$.byStage[0].stage.id").value(stage.get("Qualification")))
        .andExpect(jsonPath("$.byStage[0].totals[*].amount", contains("500.00")))
        .andExpect(jsonPath("$.byStage[1].totals", empty()))
        .andExpect(jsonPath("$.byStage[2].totals[*].currency", contains("USD")))
        .andExpect(jsonPath("$.byStage[2].totals[*].amount", contains("1000.00")))
        .andExpect(jsonPath("$.byStage[3].totals[*].currency", contains("EUR")))
        .andExpect(jsonPath("$.byStage[3].totals[*].amount", contains("300.00")))
        .andExpect(
            jsonPath(
                "$.recentActivities[*].subject",
                contains("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8")))
        .andExpect(jsonPath("$.recentActivities[0].author.email").value(ownerEmail))
        .andExpect(jsonPath("$.recentActivities[0].company.name").value("Acme"))
        .andExpect(jsonPath("$.myOverdueTasks[*].title", contains("Owner overdue")))
        .andExpect(jsonPath("$.myOverdueTasks[0].overdue").value(true))
        .andExpect(jsonPath("$.myUpcomingTasks[*].title", contains("Owner upcoming")))
        .andExpect(jsonPath("$.myUpcomingTasks[0].assignee.email").value(ownerEmail))
        .andExpect(jsonPath("$.recentChanges.length()").value(10))
        .andExpect(jsonPath("$.recentChanges[*].entityType", everyItem(in(RECORD_TYPES))))
        .andExpect(jsonPath("$.recentChanges[0].entityType").value("COMPANY"))
        .andExpect(jsonPath("$.recentChanges[0].entityId").value(gone))
        .andExpect(jsonPath("$.recentChanges[0].action").value("ARCHIVE"))
        .andExpect(jsonPath("$.recentChanges[0].actor.email").value(ownerEmail))
        .andExpect(jsonPath("$.recentChanges[1].entityType").value("ACTIVITY"))
        .andExpect(jsonPath("$.recentChanges[1].entityLabel").value("A9"));

    // Any member reads it; the "my" lists are the caller's, the counts the workspace's.
    dashboard(rep, w)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts.overdueTasks").value(2))
        .andExpect(jsonPath("$.myOverdueTasks[*].title", contains("Rep overdue")))
        .andExpect(jsonPath("$.myUpcomingTasks", empty()))
        .andExpect(jsonPath("$.openPipeline[*].weightedAmount", contains("225.00", "600.00")))
        .andExpect(jsonPath("$.recentChanges.length()").value(10));
  }

  /**
   * Weighted amounts are summed per currency and then rounded half up: one 0.05 at 10% is 0.005, so
   * 0.01 (half even or truncation would give 0.00); two of them are 0.010, so 0.01 (rounding each
   * first would give 0.02).
   */
  @Test
  void weightedAmountsRoundHalfUpPerCurrency() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Cents");
    long qualification = stages(token, w).get("Qualification");
    deal(token, w, "One", "0.05", "USD", qualification, null);
    deal(token, w, "Two", "0.05", "EUR", qualification, 10);
    deal(token, w, "Three", "0.05", "EUR", qualification, 10);

    dashboard(token, w)
        .andExpect(jsonPath("$.openPipeline[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.openPipeline[*].amount", contains("0.10", "0.05")))
        .andExpect(jsonPath("$.openPipeline[*].weightedAmount", contains("0.01", "0.01")));
  }

  /**
   * In Asia/Bangkok (UTC+7) the period up to 2026-10-17 ends at 17:00 UTC that day: 23:30 local
   * (16:30 UTC) is in, though it is the 17th in UTC either way; 00:00 local on the 18th is out. The
   * first instant of the default start (2026-09-18 local) is in, the one before it out.
   */
  @Test
  void closedJustBeforeMidnightBangkokCountsInPeriod() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createInZone(token, "Bangkok", "Asia/Bangkok");
    Map<String, Long> stage = stages(token, w);
    closed(token, w, stage.get("Won"), "100.00", "2026-10-17T16:30:00Z");
    closed(token, w, stage.get("Won"), "200.00", "2026-10-17T17:00:00Z");
    closed(token, w, stage.get("Won"), "400.00", "2026-09-17T17:00:00Z");
    closed(token, w, stage.get("Won"), "800.00", "2026-09-17T16:59:59Z");
    closed(token, w, stage.get("Lost"), "1.00", "2026-10-17T16:30:00Z");
    closed(token, w, stage.get("Lost"), "2.00", "2026-10-17T17:00:00Z");

    dashboard(token, w, "to", "2026-10-17")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period.from").value("2026-09-18"))
        .andExpect(jsonPath("$.period.to").value("2026-10-17"))
        .andExpect(jsonPath("$.won.count").value(2))
        .andExpect(jsonPath("$.won.totals[*].amount", contains("500.00")))
        .andExpect(jsonPath("$.lost.count").value(1));
    // The next day holds the two closed at local midnight.
    dashboard(token, w, "from", "2026-10-18", "to", "2026-10-18")
        .andExpect(jsonPath("$.won.totals[*].amount", contains("200.00")))
        .andExpect(jsonPath("$.lost.count").value(1));
  }

  /**
   * One instant, the application clock's: at 18:00 UTC on 17 October it is the 18th in Bangkok, so
   * the default period ends there; a task due an hour before is overdue and one due an hour after
   * upcoming, in the count and in the lists alike.
   */
  @Test
  void theClockDecidesTodayAndWhatIsOverdue() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createInZone(token, "Bangkok", "Asia/Bangkok");
    task(token, w, "Before", Instant.parse("2026-10-17T17:00:00Z"), null);
    task(token, w, "After", Instant.parse("2026-10-17T19:00:00Z"), null);
    clock.pin(Instant.parse("2026-10-17T18:00:00Z"));

    dashboard(token, w)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period.from").value("2026-09-19"))
        .andExpect(jsonPath("$.period.to").value("2026-10-18"))
        .andExpect(jsonPath("$.counts.overdueTasks").value(1))
        .andExpect(jsonPath("$.myOverdueTasks[*].title", contains("Before")))
        .andExpect(jsonPath("$.myOverdueTasks[0].overdue").value(true))
        .andExpect(jsonPath("$.myUpcomingTasks[*].title", contains("After")))
        .andExpect(jsonPath("$.myUpcomingTasks[0].overdue").value(false));
  }

  @Test
  void otherWorkspaceDataNeverCounted() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long mine = createWorkspace(owner, "Mine");
    // Another workspace with data of every kind, whose owner is also a member of mine.
    String otherEmail = uniqueEmail();
    String other = tokenFor(otherEmail);
    long theirs = createWorkspace(other, "Theirs");
    addMember(mine, otherEmail, WorkspaceRole.SALES_REP);
    Map<String, Long> stage = stages(other, theirs);
    long company = create(other, theirs, "/companies", Map.of("name", "Theirs Inc"));
    create(other, theirs, "/contacts", Map.of("firstName", "Theo"));
    create(other, theirs, "/leads", Map.of("firstName", "Lea"));
    deal(other, theirs, "Open USD", "10.00", "USD", stage.get("Proposal"), null);
    deal(other, theirs, "Open EUR", "20.00", "EUR", stage.get("Qualification"), null);
    long won = deal(other, theirs, "Won", "30.00", "USD", stage.get("Proposal"), null);
    move(other, theirs, won, stage.get("Won"), null);
    long lost = deal(other, theirs, "Lost", "40.00", "USD", stage.get("Proposal"), null);
    move(other, theirs, lost, stage.get("Lost"), "No");
    task(other, theirs, "Their overdue", hours(-1), null);
    task(other, theirs, "Their upcoming", hours(1), null);
    create(
        other,
        theirs,
        "/activities",
        Map.of("type", "NOTE", "subject", "Theirs", "companyId", company));

    // Mine is empty, also for the member whose tasks over there are overdue.
    assertAllZeros(dashboard(owner, mine).andExpect(status().isOk()));
    assertAllZeros(dashboard(other, mine).andExpect(status().isOk()));
    // Theirs has it all, so the data was there to leak.
    dashboard(other, theirs)
        .andExpect(jsonPath("$.counts.companies").value(1))
        .andExpect(jsonPath("$.counts.contacts").value(1))
        .andExpect(jsonPath("$.counts.openLeads").value(1))
        .andExpect(jsonPath("$.counts.openDeals").value(2))
        .andExpect(jsonPath("$.counts.overdueTasks").value(1))
        .andExpect(jsonPath("$.openPipeline[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.won.count").value(1))
        .andExpect(jsonPath("$.lost.count").value(1))
        .andExpect(jsonPath("$.recentActivities.length()").value(1))
        .andExpect(jsonPath("$.myOverdueTasks.length()").value(1))
        .andExpect(jsonPath("$.myUpcomingTasks.length()").value(1))
        .andExpect(jsonPath("$.recentChanges.length()").value(10));
  }

  @Test
  void badPeriodsAre400OnTheirField() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    badPeriod(token, w, "from", "from", "2026-10-18", "to", "2026-10-17");
    badPeriod(token, w, "to", "from", "2025-10-16", "to", "2026-10-17");
    badPeriod(token, w, "from", "from", "2026-13-01");
    badPeriod(token, w, "to", "to", "yesterday");
    badPeriod(token, w, "to", "to", "+10000-01-01");
    // 366 days, both ends included, is the longest.
    dashboard(token, w, "from", "2025-10-17", "to", "2026-10-17")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period.from").value("2025-10-17"));
  }

  private void assertAllZeros(ResultActions result) throws Exception {
    result
        .andExpect(jsonPath("$.counts.companies").value(0))
        .andExpect(jsonPath("$.counts.contacts").value(0))
        .andExpect(jsonPath("$.counts.openLeads").value(0))
        .andExpect(jsonPath("$.counts.openDeals").value(0))
        .andExpect(jsonPath("$.counts.overdueTasks").value(0))
        .andExpect(jsonPath("$.openPipeline", empty()))
        .andExpect(jsonPath("$.won.count").value(0))
        .andExpect(jsonPath("$.won.totals", empty()))
        .andExpect(jsonPath("$.lost.count").value(0))
        .andExpect(
            jsonPath(
                "$.byStage[*].stage.name",
                contains("Qualification", "Needs analysis", "Proposal", "Negotiation")))
        .andExpect(jsonPath("$.byStage[*].count", contains(0, 0, 0, 0)))
        .andExpect(jsonPath("$.byStage[*].totals[*]", empty()))
        .andExpect(jsonPath("$.recentActivities", empty()))
        .andExpect(jsonPath("$.myUpcomingTasks", empty()))
        .andExpect(jsonPath("$.myOverdueTasks", empty()))
        .andExpect(jsonPath("$.recentChanges", empty()));
  }

  private void badPeriod(String token, long w, String field, String... params) throws Exception {
    dashboard(token, w, params)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors." + field).isString());
  }

  /** A won or lost deal whose closedAt is then set to {@code closedAt}. */
  private void closed(String token, long w, long stageId, String amount, String closedAt)
      throws Exception {
    long id = deal(token, w, amount, amount, null, null, null);
    move(token, w, id, stageId, "Reason");
    jdbc.update(
        "UPDATE deals SET closed_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(Instant.parse(closedAt), ZoneOffset.UTC),
        id);
  }

  private long deal(
      String token,
      long w,
      String name,
      String amount,
      String currency,
      Long stageId,
      Integer probability)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("name", name);
    input.put("amount", amount);
    input.put("currency", currency);
    input.put("stageId", stageId);
    input.put("probability", probability);
    return create(token, w, "/deals", input);
  }

  private void move(String token, long w, long deal, long stageId, String lostReason)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("stageId", stageId);
    input.put("lostReason", lostReason);
    input.put("version", 0);
    send(token, post(W + "/deals/{id}/move", w, deal), input).andExpect(status().isOk());
  }

  private void leadStatus(String token, long w, long lead, String newStatus, long version)
      throws Exception {
    send(
            token,
            post(W + "/leads/{id}/status", w, lead),
            Map.of("status", newStatus, "reason", "Because", "version", version))
        .andExpect(status().isOk());
  }

  private long task(String token, long w, String title, Instant dueAt, Long assigneeId)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("title", title);
    input.put("dueAt", dueAt == null ? null : dueAt.toString());
    input.put("assigneeId", assigneeId);
    return create(token, w, "/tasks", input);
  }

  private long create(String token, long w, String path, Map<String, ?> input) throws Exception {
    return body(send(token, post(W + path, w), input).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private Map<String, Long> stages(String token, long w) throws Exception {
    Map<String, Long> ids = new HashMap<>();
    for (JsonNode s : body(send(token, get(W + "/pipeline-stages", w)))) {
      ids.put(s.get("name").asText(), s.get("id").asLong());
    }
    return ids;
  }

  private static Instant hours(long fromNow) {
    return Instant.now().plus(fromNow, ChronoUnit.HOURS);
  }

  private ResultActions dashboard(String token, long w, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(DASHBOARD, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  /** Like {@code createWorkspace}, in the given time zone. */
  private long createInZone(String token, String name, String zone) throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/workspaces")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("name", name, "defaultCurrency", "EUR", "timezone", zone))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asLong();
  }
}
