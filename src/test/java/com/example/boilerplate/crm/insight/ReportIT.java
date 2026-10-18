package com.example.boilerplate.crm.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ReportIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";
  static final List<String> REPORTS =
      List.of("pipeline", "won-lost", "funnel", "activities", "follow-ups");
  static final List<String> OPEN_STAGES =
      List.of("Qualification", "Needs analysis", "Proposal", "Negotiation");

  @Autowired JdbcTemplate jdbc;

  @Test
  void emptyWorkspaceReportsZeros() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Empty");

    report(owner, w, "pipeline")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byStage[*].stage.name", contains(OPEN_STAGES.toArray())))
        .andExpect(jsonPath("$.byStage[*].count", contains(0, 0, 0, 0)))
        .andExpect(jsonPath("$.byStage[*].totals[*]", empty()))
        .andExpect(jsonPath("$.byOwner", empty()));
    report(owner, w, "won-lost", "from", "2026-08-15", "to", "2026-10-17")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months[*].month", contains("2026-08", "2026-09", "2026-10")))
        .andExpect(jsonPath("$.months[*].won", contains(0, 0, 0)))
        .andExpect(jsonPath("$.months[*].lost", contains(0, 0, 0)))
        .andExpect(jsonPath("$.months[*].wonTotals[*]", empty()));
    report(owner, w, "funnel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.historyFrom").doesNotExist())
        .andExpect(
            jsonPath(
                "$.stages[*].stage.name",
                contains("Qualification", "Needs analysis", "Proposal", "Negotiation", "Won")))
        .andExpect(jsonPath("$.stages[*].reached", contains(0, 0, 0, 0, 0)));
    report(owner, w, "activities")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byUser", empty()));
    ResultActions followUps = report(owner, w, "follow-ups").andExpect(status().isOk());
    for (String list : List.of("overdueTasks", "upcomingTasks", "dueLeads")) {
      followUps
          .andExpect(jsonPath("$." + list + ".count").value(0))
          .andExpect(jsonPath("$." + list + ".first", empty()));
    }

    // Every member reads every report.
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    for (String r : REPORTS) report(rep, w, r).andExpect(status().isOk());
  }

  @Test
  void pipelineByStageAndOwner() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String leaverEmail = uniqueEmail();
    String leaver = tokenFor(leaverEmail);
    addMember(w, leaverEmail, WorkspaceRole.SALES_REP);
    Map<String, Long> stage = stages(owner, w);

    deal(owner, w, "1000.00", "USD", stage.get("Proposal"));
    deal(owner, w, "300.00", "EUR", stage.get("Negotiation"));
    deal(rep, w, "600.00", "USD", stage.get("Qualification"));
    deal(rep, w, "400.00", "USD", stage.get("Proposal"));
    deal(leaver, w, "2000.00", "EUR", stage.get("Qualification"));
    // Closed or archived: counted nowhere.
    long won = deal(owner, w, "5000.00", "USD", stage.get("Proposal"));
    move(owner, w, won, stage.get("Won"), 0);
    long lost = deal(rep, w, "6000.00", "USD", stage.get("Proposal"));
    move(rep, w, lost, stage.get("Lost"), 0);
    long archived = deal(rep, w, "7000.00", "EUR", stage.get("Proposal"));
    send(owner, post(W + "/deals/{id}/archive", w, archived)).andExpect(status().isOk());
    // The leaver's deal becomes unassigned.
    send(owner, delete(W + "/members/{userId}", w, userId(leaverEmail)))
        .andExpect(status().isNoContent());

    // The owner and the rep tie at USD 1000.00: then by email.
    List<String> tied = Stream.of(ownerEmail, repEmail).sorted().toList();
    int o = 1 + tied.indexOf(ownerEmail);
    int r = 1 + tied.indexOf(repEmail);
    report(owner, w, "pipeline")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byStage[*].stage.name", contains(OPEN_STAGES.toArray())))
        .andExpect(jsonPath("$.byStage[*].count", contains(2, 0, 2, 1)))
        .andExpect(jsonPath("$.byStage[0].totals[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.byStage[0].totals[*].amount", contains("2000.00", "600.00")))
        .andExpect(jsonPath("$.byStage[1].totals", empty()))
        .andExpect(jsonPath("$.byStage[2].totals[*].currency", contains("USD")))
        .andExpect(jsonPath("$.byStage[2].totals[*].amount", contains("1400.00")))
        .andExpect(jsonPath("$.byStage[3].totals[*].amount", contains("300.00")))
        // The largest single-currency total first: the unassigned EUR 2000.00.
        .andExpect(jsonPath("$.byOwner.length()").value(3))
        .andExpect(jsonPath("$.byOwner[0].owner").doesNotExist())
        .andExpect(jsonPath("$.byOwner[0].count").value(1))
        .andExpect(jsonPath("$.byOwner[0].totals[*].currency", contains("EUR")))
        .andExpect(jsonPath("$.byOwner[0].totals[*].amount", contains("2000.00")))
        .andExpect(jsonPath("$.byOwner[*].owner.email", contains(tied.toArray())))
        .andExpect(jsonPath("$.byOwner[" + o + "].count").value(2))
        .andExpect(jsonPath("$.byOwner[" + o + "].totals[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.byOwner[" + o + "].totals[*].amount", contains("300.00", "1000.00")))
        .andExpect(jsonPath("$.byOwner[" + r + "].count").value(2))
        .andExpect(jsonPath("$.byOwner[" + r + "].totals[*].currency", contains("USD")))
        .andExpect(jsonPath("$.byOwner[" + r + "].totals[*].amount", contains("1000.00")));
  }

  /**
   * In Asia/Bangkok (UTC+7) 2026-08-31T18:00Z is 1 September 01:00: a September close. The period
   * runs from 2026-07-14T17:00Z (15 July 00:00 there) to 2026-10-31T17:00Z, exclusive.
   */
  @Test
  void wonLostByMonthInWorkspaceZone() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Bangkok", "Asia/Bangkok");
    closedAcrossMonths(token, w);

    report(token, w, "won-lost", "from", "2026-07-15", "to", "2026-10-31")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.months[*].month", contains("2026-07", "2026-08", "2026-09", "2026-10")))
        .andExpect(jsonPath("$.months[*].won", contains(1, 0, 2, 0)))
        .andExpect(jsonPath("$.months[*].lost", contains(0, 1, 0, 1)))
        .andExpect(jsonPath("$.months[0].wonTotals[*].amount", contains("7.00")))
        .andExpect(jsonPath("$.months[1].wonTotals", empty()))
        .andExpect(jsonPath("$.months[2].wonTotals[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.months[2].wonTotals[*].amount", contains("40.00", "100.00")))
        .andExpect(jsonPath("$.months[3].wonTotals", empty()));
  }

  @Test
  void funnelCountsReachedStages() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Funnel");
    Map<String, Long> stage = stages(token, w);
    // A: Qualification -> Proposal -> Won (skipping Negotiation).
    long a = deal(token, w, "1.00", "EUR", stage.get("Qualification"));
    move(token, w, a, stage.get("Proposal"), 0);
    move(token, w, a, stage.get("Won"), 1);
    // B: Needs analysis -> Lost; Lost counts for nothing.
    long b = deal(token, w, "1.00", "EUR", stage.get("Needs analysis"));
    move(token, w, b, stage.get("Lost"), 0);
    // C: Qualification, through a stage after Negotiation that is deleted since: its history row
    // keeps only the name and counts for nothing.
    long c = deal(token, w, "1.00", "EUR", stage.get("Qualification"));
    long demo =
        body(send(token, post(W + "/pipeline-stages", w), Map.of("name", "Demo", "probability", 90))
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    move(token, w, c, demo, 0);
    move(token, w, c, stage.get("Qualification"), 1);
    send(token, delete(W + "/pipeline-stages/{id}", w, demo)).andExpect(status().isNoContent());
    // Not in the cohort: an archived deal, and one created before the period, whose history is
    // the workspace's earliest.
    long archived = deal(token, w, "1.00", "EUR", stage.get("Negotiation"));
    send(token, post(W + "/deals/{id}/archive", w, archived)).andExpect(status().isOk());
    long old = deal(token, w, "1.00", "EUR", stage.get("Negotiation"));
    OffsetDateTime january = OffsetDateTime.parse("2026-01-10T23:30:00Z");
    jdbc.update("UPDATE deals SET created_at = ? WHERE id = ?", january, old);
    jdbc.update("UPDATE deal_stage_changes SET changed_at = ? WHERE deal_id = ?", january, old);

    report(token, w, "funnel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(3))
        // 23:30 UTC is the next day in Paris.
        .andExpect(jsonPath("$.historyFrom").value("2026-01-11"))
        .andExpect(
            jsonPath(
                "$.stages[*].stage.name",
                contains("Qualification", "Needs analysis", "Proposal", "Negotiation", "Won")))
        .andExpect(jsonPath("$.stages[*].reached", contains(3, 2, 1, 1, 1)))
        .andExpect(jsonPath("$.stages[4].stage.kind").value("WON"));
    // The period counts by creation: January holds the old deal only.
    report(token, w, "funnel", "from", "2026-01-01", "to", "2026-01-31")
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.stages[*].reached", contains(1, 1, 1, 1, 0)));
  }

  @Test
  void activitiesByUser() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    String leaverEmail = uniqueEmail();
    String leaver = tokenFor(leaverEmail);
    addMember(w, leaverEmail, WorkspaceRole.SALES_REP);
    long company = create(owner, w, "/companies", Map.of("name", "Acme"));

    activity(owner, w, "CALL", company, hours(-1));
    activity(owner, w, "CALL", company, hours(-2));
    activity(owner, w, "NOTE", company, hours(-3));
    activity(manager, w, "MEETING", company, hours(-1));
    activity(leaver, w, "EMAIL", company, hours(-1));
    // Before the period (the last 30 days).
    activity(owner, w, "EMAIL", company, hours(-24 * 40));
    long managerId = userId(managerEmail);
    long done = task(owner, w, "Done", null, managerId);
    complete(manager, w, done);
    long doneLongAgo = task(owner, w, "Done long ago", null, managerId);
    complete(manager, w, doneLongAgo);
    jdbc.update(
        "UPDATE tasks SET completed_at = completed_at - interval '40 days' WHERE id = ?",
        doneLongAgo);
    task(owner, w, "Still open", null, managerId);
    // The leaver's activity stays, without an author.
    send(owner, delete(W + "/members/{userId}", w, userId(leaverEmail)))
        .andExpect(status().isNoContent());

    // Most in total first: the owner 3, the manager 2, the removed member 1.
    report(owner, w, "activities")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byUser.length()").value(3))
        .andExpect(jsonPath("$.byUser[*].user.email", contains(ownerEmail, managerEmail)))
        .andExpect(jsonPath("$.byUser[*].calls", contains(2, 0, 0)))
        .andExpect(jsonPath("$.byUser[*].emails", contains(0, 0, 1)))
        .andExpect(jsonPath("$.byUser[*].meetings", contains(0, 1, 0)))
        .andExpect(jsonPath("$.byUser[*].notes", contains(1, 0, 0)))
        .andExpect(jsonPath("$.byUser[*].tasksCompleted", contains(0, 1, 0)))
        .andExpect(jsonPath("$.byUser[2].user").doesNotExist());
  }

  /** "Today" for the leads is the workspace's: Pacific/Kiritimati is UTC+14. */
  @Test
  void followUps() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Kiritimati", "Pacific/Kiritimati");
    String repEmail = uniqueEmail();
    tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long repId = userId(repEmail);

    // 21 overdue, every other one the rep's: workspace-wide, longest overdue first, 20 listed.
    for (int i = 1; i <= 21; i++) {
      task(owner, w, "Overdue " + i, hours(-i), i % 2 == 0 ? repId : null);
    }
    // Upcoming: due in the next 7 days, soonest first.
    task(owner, w, "In 3 days", hours(72), null);
    task(owner, w, "In 1 hour", hours(1), repId);
    task(owner, w, "In 8 days", hours(24 * 8), null);
    // Not open, or undated: neither.
    complete(owner, w, task(owner, w, "Completed", hours(-30), null));
    long cancelled = task(owner, w, "Cancelled", hours(2), null);
    send(owner, post(W + "/tasks/{id}/cancel", w, cancelled), Map.of("version", 0))
        .andExpect(status().isOk());
    task(owner, w, "Undated", null, null);

    // Open active leads with a follow-up date up to today, oldest date first.
    LocalDate today = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
    lead(owner, w, "Today", today);
    lead(owner, w, "Last week", today.minusDays(7));
    long contacted = lead(owner, w, "Contacted", today.minusDays(3));
    leadStatus(owner, w, contacted, "CONTACTED");
    lead(owner, w, "Tomorrow", today.plusDays(1));
    long unqualified = lead(owner, w, "Unqualified", today.minusDays(1));
    leadStatus(owner, w, unqualified, "UNQUALIFIED");
    long archived = lead(owner, w, "Archived", today.minusDays(2));
    send(owner, post(W + "/leads/{id}/archive", w, archived)).andExpect(status().isOk());
    lead(owner, w, "Undated", null);

    report(owner, w, "follow-ups")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdueTasks.count").value(21))
        .andExpect(jsonPath("$.overdueTasks.first.length()").value(20))
        .andExpect(jsonPath("$.overdueTasks.first[0].title").value("Overdue 21"))
        .andExpect(jsonPath("$.overdueTasks.first[1].assignee.email").value(repEmail))
        .andExpect(jsonPath("$.overdueTasks.first[19].title").value("Overdue 2"))
        .andExpect(jsonPath("$.overdueTasks.first[0].overdue").value(true))
        .andExpect(jsonPath("$.upcomingTasks.count").value(2))
        .andExpect(jsonPath("$.upcomingTasks.first[*].title", contains("In 1 hour", "In 3 days")))
        .andExpect(jsonPath("$.dueLeads.count").value(3))
        .andExpect(
            jsonPath("$.dueLeads.first[*].firstName", contains("Last week", "Contacted", "Today")))
        .andExpect(jsonPath("$.dueLeads.first[2].followUpDate").value(today.toString()));
  }

  /**
   * The same data in another workspace, whose owner is also a member of the first, leaves the
   * first's reports as they were (the funnel's native SQL binds the workspace itself).
   */
  @Test
  void nativeQueriesIgnoreOtherWorkspaces() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long mine = createWorkspace(owner, "Mine");
    String otherEmail = uniqueEmail();
    String other = tokenFor(otherEmail);
    long theirs = createWorkspace(other, "Theirs");
    addMember(mine, otherEmail, WorkspaceRole.SALES_REP);

    seed(owner, mine);
    Map<String, String> before = reports(owner, mine);
    seed(other, theirs);

    assertThat(reports(owner, mine)).isEqualTo(before);
    // Both hold the data: it was there to leak.
    for (var workspace : Map.of(owner, mine, other, theirs).entrySet()) {
      String token = workspace.getKey();
      long w = workspace.getValue();
      report(token, w, "funnel")
          .andExpect(jsonPath("$.created").value(3))
          .andExpect(jsonPath("$.stages[*].reached", contains(3, 3, 2, 2, 1)));
      report(token, w, "pipeline").andExpect(jsonPath("$.byStage[*].count", contains(0, 0, 0, 1)));
      report(token, w, "won-lost").andExpect(jsonPath("$.months[-1].won").value(1));
      report(token, w, "activities").andExpect(jsonPath("$.byUser[0].calls").value(1));
      report(token, w, "follow-ups")
          .andExpect(jsonPath("$.overdueTasks.count").value(1))
          .andExpect(jsonPath("$.upcomingTasks.count").value(1))
          .andExpect(jsonPath("$.dueLeads.count").value(1));
    }
  }

  /** The three period reports return the period they used, as the dashboard does. */
  @Test
  void periodReportsReturnTheirPeriod() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Bangkok", "Asia/Bangkok");
    ZoneId zone = ZoneId.of("Asia/Bangkok");
    for (String r : List.of("won-lost", "funnel", "activities")) {
      // The default: 30 days, up to today in the workspace's zone.
      LocalDate before = LocalDate.now(zone);
      JsonNode period = body(report(token, w, r).andExpect(status().isOk())).get("period");
      LocalDate after = LocalDate.now(zone);
      LocalDate to = LocalDate.parse(period.get("to").asText());
      assertThat(to).as(r).isIn(before, after);
      assertThat(LocalDate.parse(period.get("from").asText())).as(r).isEqualTo(to.minusDays(29));

      report(token, w, r, "from", "2026-08-15", "to", "2026-10-17")
          .andExpect(jsonPath("$.period.from").value("2026-08-15"))
          .andExpect(jsonPath("$.period.to").value("2026-10-17"));
      // A blank date counts as absent: from is 29 days before to.
      report(token, w, r, "from", "", "to", "2026-10-17")
          .andExpect(jsonPath("$.period.from").value("2026-09-18"))
          .andExpect(jsonPath("$.period.to").value("2026-10-17"));
    }
  }

  /** One period: the dashboard's won and lost are the sums over the won-lost months. */
  @Test
  void dashboardWonLostIsTheSumOverTheMonths() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Bangkok", "Asia/Bangkok");
    closedAcrossMonths(token, w);

    JsonNode months =
        body(send(
                token,
                get(W + "/reports/won-lost", w)
                    .param("from", "2026-07-15")
                    .param("to", "2026-10-31")))
            .get("months");
    JsonNode dashboard =
        body(
            send(
                token,
                get(W + "/dashboard", w).param("from", "2026-07-15").param("to", "2026-10-31")));

    long won = 0;
    long lost = 0;
    Map<String, BigDecimal> totals = new TreeMap<>();
    for (JsonNode month : months) {
      won += month.get("won").asLong();
      lost += month.get("lost").asLong();
      for (JsonNode t : month.get("wonTotals")) {
        totals.merge(
            t.get("currency").asText(), new BigDecimal(t.get("amount").asText()), BigDecimal::add);
      }
    }
    Map<String, BigDecimal> dashboardTotals = new TreeMap<>();
    for (JsonNode t : dashboard.at("/won/totals")) {
      dashboardTotals.put(t.get("currency").asText(), new BigDecimal(t.get("amount").asText()));
    }
    // Not vacuous: 3 won (EUR 40.00, USD 107.00) and 2 lost.
    assertThat(won).isEqualTo(3);
    assertThat(lost).isEqualTo(2);
    assertThat(totals).containsOnlyKeys("EUR", "USD");
    assertThat(dashboard.at("/won/count").asLong()).isEqualTo(won);
    assertThat(dashboard.at("/lost/count").asLong()).isEqualTo(lost);
    assertThat(dashboardTotals).isEqualTo(totals);
  }

  @Test
  void badPeriodsAre400OnTheirField() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    for (String r : List.of("won-lost", "funnel", "activities")) {
      badPeriod(token, w, r, "from", "from", "2026-10-18", "to", "2026-10-17");
      badPeriod(token, w, r, "to", "from", "2025-10-16", "to", "2026-10-17");
      badPeriod(token, w, r, "to", "to", "yesterday");
      report(token, w, r, "from", "2025-10-17", "to", "2026-10-17").andExpect(status().isOk());
    }
  }

  /**
   * Deals closed around the month borders of Asia/Bangkok, for the period 2026-07-15 to 2026-10-31:
   * 3 won (July, September twice) and 2 lost (August, October), one more of each just outside, and
   * an archived one.
   */
  private void closedAcrossMonths(String token, long w) throws Exception {
    Map<String, Long> stage = stages(token, w);
    long won = stage.get("Won");
    long lost = stage.get("Lost");
    closed(token, w, won, "7.00", "USD", "2026-07-14T17:00:00Z");
    closed(token, w, won, "9.00", "USD", "2026-07-14T16:59:59Z");
    closed(token, w, lost, "1.00", "USD", "2026-08-31T16:59:59Z");
    closed(token, w, won, "100.00", "USD", "2026-08-31T18:00:00Z");
    closed(token, w, won, "40.00", "EUR", "2026-09-15T00:00:00Z");
    long archived = closed(token, w, won, "500.00", "EUR", "2026-09-16T00:00:00Z");
    send(token, post(W + "/deals/{id}/archive", w, archived)).andExpect(status().isOk());
    closed(token, w, lost, "2.00", "USD", "2026-10-31T16:59:59Z");
    closed(token, w, won, "11.00", "USD", "2026-10-31T17:00:00Z");
  }

  /** One of everything the reports count, by the token's user. */
  private void seed(String token, long w) throws Exception {
    Map<String, Long> stage = stages(token, w);
    long company = create(token, w, "/companies", Map.of("name", "Acme"));
    long won = deal(token, w, "100.00", "USD", stage.get("Qualification"));
    move(token, w, won, stage.get("Proposal"), 0);
    move(token, w, won, stage.get("Won"), 1);
    long lost = deal(token, w, "50.00", "EUR", stage.get("Needs analysis"));
    move(token, w, lost, stage.get("Lost"), 0);
    deal(token, w, "70.00", "USD", stage.get("Negotiation"));
    activity(token, w, "CALL", company, hours(-1));
    complete(token, w, task(token, w, "Done", null, null));
    task(token, w, "Overdue", hours(-48), null);
    task(token, w, "Upcoming", hours(48), null);
    lead(token, w, "Due", LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1));
  }

  /** Every report's body, by report. */
  private Map<String, String> reports(String token, long w) throws Exception {
    Map<String, String> bodies = new LinkedHashMap<>();
    for (String r : REPORTS) {
      bodies.put(
          r,
          report(token, w, r)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString());
    }
    return bodies;
  }

  private void badPeriod(String token, long w, String report, String field, String... params)
      throws Exception {
    report(token, w, report, params)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors." + field).isString());
  }

  private ResultActions report(String token, long w, String report, String... params)
      throws Exception {
    MockHttpServletRequestBuilder request = get(W + "/reports/" + report, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  /** A won or lost deal whose closedAt is then set to {@code closedAt}. */
  private long closed(
      String token, long w, long stageId, String amount, String currency, String closedAt)
      throws Exception {
    long id = deal(token, w, amount, currency, null);
    move(token, w, id, stageId, 0);
    jdbc.update(
        "UPDATE deals SET closed_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(Instant.parse(closedAt), ZoneOffset.UTC),
        id);
    return id;
  }

  private long deal(String token, long w, String amount, String currency, Long stageId)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("name", currency + " " + amount);
    input.put("amount", amount);
    input.put("currency", currency);
    input.put("stageId", stageId);
    return create(token, w, "/deals", input);
  }

  private void move(String token, long w, long deal, long stageId, long version) throws Exception {
    send(
            token,
            post(W + "/deals/{id}/move", w, deal),
            Map.of("stageId", stageId, "lostReason", "Reason", "version", version))
        .andExpect(status().isOk());
  }

  private void activity(String token, long w, String type, long company, Instant occurredAt)
      throws Exception {
    create(
        token,
        w,
        "/activities",
        Map.of(
            "type",
            type,
            "subject",
            type,
            "occurredAt",
            occurredAt.toString(),
            "companyId",
            company));
  }

  private long task(String token, long w, String title, Instant dueAt, Long assigneeId)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("title", title);
    input.put("dueAt", dueAt == null ? null : dueAt.toString());
    input.put("assigneeId", assigneeId);
    return create(token, w, "/tasks", input);
  }

  private void complete(String token, long w, long task) throws Exception {
    send(token, post(W + "/tasks/{id}/complete", w, task), Map.of("version", 0))
        .andExpect(status().isOk());
  }

  private long lead(String token, long w, String firstName, LocalDate followUpDate)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("firstName", firstName);
    input.put("followUpDate", followUpDate == null ? null : followUpDate.toString());
    return create(token, w, "/leads", input);
  }

  private void leadStatus(String token, long w, long lead, String newStatus) throws Exception {
    send(
            token,
            post(W + "/leads/{id}/status", w, lead),
            Map.of("status", newStatus, "reason", "Because", "version", 0))
        .andExpect(status().isOk());
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

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private static Instant hours(long fromNow) {
    return Instant.now().plus(fromNow, ChronoUnit.HOURS);
  }
}
