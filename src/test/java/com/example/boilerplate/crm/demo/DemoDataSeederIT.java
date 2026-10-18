package com.example.boilerplate.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoDataSeederIT extends AbstractIntegrationTest {
  /** {@code app.demo.password} of application-test.yml. */
  static final String DEMO_PASSWORD = "test-demo-password";

  static final List<String> TABLES =
      List.of(
          "companies",
          "contacts",
          "leads",
          "deals",
          "tasks",
          "activities",
          "deal_stage_changes",
          "audit_events");

  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate jdbc;

  @Test
  void seedsOnceWithAllRolesAndData() throws Exception {
    // A local/dev bean, so not in this context: built from it, with application-test.yml's
    // app.demo.*, and run as the application would run it.
    DemoDataSeeder seeder =
        context.getAutowireCapableBeanFactory().createBean(DemoDataSeeder.class);
    seeder.run(new DefaultApplicationArguments());

    long w = workspace();
    assertThat(
            jdbc.queryForMap("SELECT default_currency, timezone FROM workspaces WHERE id = ?", w))
        .containsEntry("default_currency", "USD")
        .containsEntry("timezone", "Asia/Bangkok");
    assertThat(
            jdbc.queryForList(
                "SELECT u.email || ' ' || u.display_name || ' ' || m.role FROM workspace_members m"
                    + " JOIN users u ON u.id = m.user_id WHERE m.workspace_id = ? ORDER BY u.email",
                String.class,
                w))
        .containsExactly(
            "admin@demo.test Sam Admin ADMIN",
            "manager@demo.test Morgan Manager MANAGER",
            "owner@demo.test Avery Owner OWNER",
            "rep@demo.test Riley Rep SALES_REP");
    Map<String, Long> counts = counts(w);
    assertThat(counts)
        .containsEntry("companies", 12L)
        .containsEntry("contacts", 30L)
        .containsEntry("leads", 15L)
        .containsEntry("deals", 24L)
        .containsEntry("tasks", 30L)
        .containsEntry("activities", 60L);

    assertThat(
            count("SELECT count(*) FROM contacts WHERE workspace_id = ? AND 'vip' = ANY(tags)", w))
        .isPositive();
    assertThat(
            count(
                "SELECT count(*) FROM contacts WHERE workspace_id = ? AND 'partner' = ANY(tags)",
                w))
        .isPositive();
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT status FROM leads WHERE workspace_id = ?", String.class, w))
        .containsExactlyInAnyOrder("NEW", "CONTACTED", "QUALIFIED", "UNQUALIFIED", "CONVERTED");
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT s.name FROM deals d JOIN pipeline_stages s ON s.id = d.stage_id"
                    + " WHERE d.workspace_id = ?",
                String.class,
                w))
        .containsExactlyInAnyOrder(
            "Qualification", "Needs analysis", "Proposal", "Negotiation", "Won", "Lost");
    assertThat(count("SELECT count(*) FROM deals WHERE workspace_id = ? AND currency = 'EUR'", w))
        .isBetween(1L, 8L);
    assertThat(
            count(
                "SELECT count(*) FROM tasks WHERE workspace_id = ? AND status = 'OPEN'"
                    + " AND due_at < now()",
                w))
        .as("overdue")
        .isPositive();
    assertThat(
            count(
                "SELECT count(*) FROM tasks WHERE workspace_id = ? AND status = 'OPEN'"
                    + " AND due_at >= now() AND due_at < now() + interval '7 days'",
                w))
        .as("due soon")
        .isPositive();
    assertThat(
            count("SELECT count(*) FROM tasks WHERE workspace_id = ? AND status = 'COMPLETED'", w))
        .isPositive();
    // Fictional addresses only, apart from the four logins.
    assertThat(
            jdbc.queryForList(
                "SELECT email FROM contacts WHERE workspace_id = ?"
                    + " UNION ALL SELECT email FROM leads WHERE workspace_id = ?",
                String.class,
                w,
                w))
        .hasSize(45)
        .allMatch(e -> e.endsWith("@example.com"));

    // The back-dated history stays what the services would have written: every deal starts with
    // its creation entry at its createdAt, the moves follow in order, the close is the last move.
    assertThat(
            count(
                "SELECT count(*) FROM deal_stage_changes c JOIN deals d ON d.id = c.deal_id"
                    + " WHERE d.workspace_id = ? AND c.from_stage_id IS NULL"
                    + " AND c.changed_at = d.created_at",
                w))
        .isEqualTo(24);
    assertThat(
            count(
                "SELECT count(*) FROM deal_stage_changes a JOIN deal_stage_changes b"
                    + " ON b.deal_id = a.deal_id AND b.id > a.id"
                    + " WHERE a.workspace_id = ? AND b.changed_at <= a.changed_at",
                w))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM deals d WHERE d.workspace_id = ?"
                    + " AND d.created_at > now() - interval '180 days'"
                    + " AND d.created_at < now() - interval '1 day'",
                w))
        .isEqualTo(24);
    long won = closed(w, "WON");
    long lost = closed(w, "LOST");
    assertThat(won).isPositive();
    assertThat(lost).isPositive();
    assertThat(
            count(
                "SELECT count(*) FROM deals d JOIN pipeline_stages s ON s.id = d.stage_id"
                    + " WHERE d.workspace_id = ? AND s.kind <> 'OPEN' AND d.closed_at ="
                    + " (SELECT max(changed_at) FROM deal_stage_changes c WHERE c.deal_id = d.id)",
                w))
        .isEqualTo(won + lost);
    assertThat(count("SELECT count(*) FROM deals WHERE workspace_id = ? AND closed_at IS NULL", w))
        .isEqualTo(24 - won - lost);

    // The reports read that history and agree with it. The owner signs in with the password.
    String token = login("owner@demo.test", DEMO_PASSWORD).get("accessToken").asText();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
    String period = "?from=" + today.minusDays(200) + "&to=" + today;
    JsonNode funnel = report(token, w, "funnel" + period);
    assertThat(funnel.get("created").asLong()).isEqualTo(24);
    assertThat(funnel.get("stages").get(4).get("stage").get("name").asText()).isEqualTo("Won");
    assertThat(funnel.get("stages").get(4).get("reached").asLong()).isEqualTo(won);
    assertThat(funnel.get("historyFrom").asText())
        .isEqualTo(
            jdbc.queryForObject(
                "SELECT (min(created_at) AT TIME ZONE 'Asia/Bangkok')::date::text FROM deals"
                    + " WHERE workspace_id = ?",
                String.class,
                w));
    JsonNode months = report(token, w, "won-lost" + period).get("months");
    long reportedWon = 0;
    long reportedLost = 0;
    for (JsonNode m : months) {
      reportedWon += m.get("won").asLong();
      reportedLost += m.get("lost").asLong();
    }
    assertThat(reportedWon).isEqualTo(won);
    assertThat(reportedLost).isEqualTo(lost);
    for (String login : List.of("admin@demo.test", "manager@demo.test", "rep@demo.test")) {
      login(login, DEMO_PASSWORD);
    }

    // Once: a second start finds the workspace and writes nothing.
    seeder.run(new DefaultApplicationArguments());
    assertThat(workspace()).isEqualTo(w);
    assertThat(counts(w)).isEqualTo(counts);
  }

  @Test
  void notInProdProfile() {
    assertThat(DemoDataSeeder.class.getAnnotation(Profile.class).value())
        .containsExactlyInAnyOrder("local", "dev");
    assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
  }

  /** The one demo workspace. */
  private long workspace() {
    List<Long> ids =
        jdbc.queryForList(
            "SELECT id FROM workspaces WHERE name = ?", Long.class, DemoDataSeeder.WORKSPACE);
    assertThat(ids).hasSize(1);
    return ids.get(0);
  }

  private Map<String, Long> counts(long w) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String table : TABLES) {
      counts.put(table, count("SELECT count(*) FROM " + table + " WHERE workspace_id = ?", w));
    }
    return counts;
  }

  private long closed(long w, String kind) {
    return count(
        "SELECT count(*) FROM deals d JOIN pipeline_stages s ON s.id = d.stage_id"
            + " WHERE d.workspace_id = ? AND s.kind = '"
            + kind
            + "'",
        w);
  }

  private long count(String sql, long w) {
    return jdbc.queryForObject(sql, Long.class, w);
  }

  private JsonNode report(String token, long w, String path) throws Exception {
    return json.readTree(
        mvc.perform(
                get("/api/v1/workspaces/{w}/reports/" + path, w)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
