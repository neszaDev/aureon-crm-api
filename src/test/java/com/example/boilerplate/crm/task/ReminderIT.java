package com.example.boilerplate.crm.task;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReminderIT extends WorkspaceTestSupport {
  static final String REMINDERS = "/api/v1/workspaces/{workspaceId}/reminders";
  static final String TASKS = "/api/v1/workspaces/{workspaceId}/tasks";
  static final String TASK = TASKS + "/{id}";
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";
  static final String LEAD = LEADS + "/{id}";

  @Test
  void remindersAreTheCallersOnly() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long repId = users.findByEmail(repEmail).orElseThrow().getId();

    task(owner, w, "overdue", hours(-26), null);
    task(owner, w, "due in 2 h", hours(2), null);
    task(owner, w, "due in 3 days", hours(72), null);
    task(owner, w, "due in 30 h", hours(30), null);
    task(owner, w, "no due date", null, null);
    long done = task(owner, w, "overdue but done", hours(-5), null);
    long dropped = task(owner, w, "soon but cancelled", hours(1), null);
    long closedSoon = task(owner, w, "soon but done", hours(3), null);
    // Another member's tasks, however overdue, are not the owner's.
    task(owner, w, "rep's overdue", hours(-48), repId);
    task(rep, w, "rep's own, soon", hours(4), null);
    action(owner, w, done, "complete");
    action(owner, w, dropped, "cancel");
    action(owner, w, closedSoon, "complete");

    // Overdue and due within the next 24 hours; no due date, later, finished and cancelled ones
    // are in neither list.
    reminders(owner, w)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdueTasks[*].title", contains("overdue")))
        .andExpect(jsonPath("$.overdueTasks[0].overdue").value(true))
        .andExpect(jsonPath("$.overdueTasks[0].assignee.email").value(ownerEmail))
        .andExpect(jsonPath("$.dueSoonTasks[*].title", contains("due in 2 h")))
        .andExpect(jsonPath("$.dueSoonTasks[0].overdue").value(false))
        .andExpect(jsonPath("$.followUpLeads.length()").value(0));
    reminders(rep, w)
        .andExpect(jsonPath("$.overdueTasks[*].title", contains("rep's overdue")))
        .andExpect(jsonPath("$.dueSoonTasks[*].title", contains("rep's own, soon")))
        .andExpect(jsonPath("$.followUpLeads.length()").value(0));
  }

  @Test
  void tasksComeSoonestFirstAtMostTwentyEach() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    // 22 of each, created out of order; the 20 soonest due first (for overdue: the longest ago).
    for (int i = 22; i >= 1; i--) {
      task(token, w, "late " + i, hours(-i), null);
      task(token, w, "soon " + i, minutes(10L * i), null);
    }

    reminders(token, w)
        .andExpect(jsonPath("$.overdueTasks.length()").value(20))
        .andExpect(jsonPath("$.overdueTasks[0].title").value("late 22"))
        .andExpect(jsonPath("$.overdueTasks[19].title").value("late 3"))
        .andExpect(jsonPath("$.dueSoonTasks.length()").value(20))
        .andExpect(jsonPath("$.dueSoonTasks[0].title").value("soon 1"))
        .andExpect(jsonPath("$.dueSoonTasks[19].title").value("soon 20"));
  }

  /**
   * "Today" is the workspace's, not the server's or UTC's: Pacific/Kiritimati (UTC+14) and
   * Pacific/Pago_Pago (UTC-11) are 25 hours apart, so their dates differ at any moment and no
   * single other zone agrees with both.
   */
  @Test
  void followUpLeadsAreDueTodayInTheWorkspaceTimeZone() throws Exception {
    for (String zone : new String[] {"Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC"}) {
      String email = uniqueEmail();
      String token = tokenFor(email);
      long w = createInZone(token, "Zone " + zone, zone);
      LocalDate today = LocalDate.now(ZoneId.of(zone));

      lead(token, w, "Tomorrow", today.plusDays(1));
      lead(token, w, "Today", today);
      lead(token, w, "Yesterday", today.minusDays(1));
      lead(token, w, "Last week", today.minusDays(7));
      lead(token, w, "Never", null);

      // The date equal to the workspace's today is in; tomorrow's is out; oldest first.
      reminders(token, w)
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.followUpLeads[*].firstName", contains("Last week", "Yesterday", "Today")))
          .andExpect(jsonPath("$.followUpLeads[2].followUpDate").value(today.toString()))
          .andExpect(jsonPath("$.overdueTasks.length()").value(0))
          .andExpect(jsonPath("$.dueSoonTasks.length()").value(0));
    }
  }

  @Test
  void followUpLeadsAreTheCallersOpenActiveOnes() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    LocalDate due = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(2);

    lead(rep, w, "Mine", due);
    long contacted = lead(rep, w, "Contacted", due.plusDays(1));
    long qualified = lead(rep, w, "Qualified", due.plusDays(1));
    long unqualified = lead(rep, w, "Unqualified", due);
    long archived = lead(rep, w, "Archived", due);
    lead(owner, w, "Owner's", due);
    setStatus(rep, w, contacted, "CONTACTED");
    setStatus(rep, w, qualified, "QUALIFIED");
    setStatus(rep, w, unqualified, "UNQUALIFIED");
    send(rep, post(LEAD + "/archive", w, archived)).andExpect(status().isOk());
    long converted = lead(rep, w, "Converted", due);
    setStatus(rep, w, converted, "QUALIFIED");
    send(rep, post(LEAD + "/convert", w, converted), Map.of("createCompany", false))
        .andExpect(status().isOk());

    reminders(rep, w)
        .andExpect(
            jsonPath("$.followUpLeads[*].firstName", contains("Mine", "Contacted", "Qualified")))
        .andExpect(jsonPath("$.followUpLeads[0].owner.email").value(repEmail))
        .andExpect(jsonPath("$.followUpLeads[0].status").value("NEW"));
    reminders(owner, w).andExpect(jsonPath("$.followUpLeads[*].firstName", contains("Owner's")));
  }

  private static Instant hours(long fromNow) {
    return Instant.now().plus(fromNow, ChronoUnit.HOURS);
  }

  private static Instant minutes(long fromNow) {
    return Instant.now().plus(fromNow, ChronoUnit.MINUTES);
  }

  /** A task due at {@code dueAt} (or never), for {@code assigneeId} (or the caller). */
  private long task(String token, long w, String title, Instant dueAt, Long assigneeId)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("title", title);
    input.put("dueAt", dueAt == null ? null : dueAt.toString());
    input.put("assigneeId", assigneeId);
    return body(send(token, post(TASKS, w), input).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private void action(String token, long w, long id, String action) throws Exception {
    send(token, post(TASK + "/" + action, w, id), Map.of("version", 0)).andExpect(status().isOk());
  }

  private long lead(String token, long w, String firstName, LocalDate followUpDate)
      throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("firstName", firstName);
    input.put("followUpDate", followUpDate == null ? null : followUpDate.toString());
    return body(send(token, post(LEADS, w), input).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private void setStatus(String token, long w, long lead, String newStatus) throws Exception {
    Map<String, Object> input = new HashMap<>();
    input.put("status", newStatus);
    input.put("reason", "No budget");
    input.put("version", 0);
    send(token, post(LEAD + "/status", w, lead), input).andExpect(status().isOk());
  }

  private ResultActions reminders(String token, long w) throws Exception {
    return send(token, get(REMINDERS, w));
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
