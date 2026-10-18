package com.example.boilerplate.crm.insight;

import com.example.boilerplate.audit.AuditEventService;
import com.example.boilerplate.crm.activity.ActivityService;
import com.example.boilerplate.crm.activity.dto.ActivityFilter;
import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.insight.dto.*;
import com.example.boilerplate.crm.lead.LeadRepository;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.crm.settings.PipelineStageService;
import com.example.boilerplate.crm.settings.StageKind;
import com.example.boilerplate.crm.settings.dto.StageResponse;
import com.example.boilerplate.crm.task.TaskRepository;
import com.example.boilerplate.crm.task.TaskService;
import com.example.boilerplate.crm.task.TaskSort;
import com.example.boilerplate.crm.task.TaskStatus;
import com.example.boilerplate.crm.task.dto.TaskFilter;
import com.example.boilerplate.crm.task.dto.TaskResponse;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard of the current workspace, computed from the stored data on every request. Every
 * query is tenant-filtered JPQL, except the audit events', which bind the workspace explicitly.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
  static final int RECENT_ACTIVITIES = 8;
  static final int MY_TASKS = 8;
  static final int RECENT_CHANGES = 10;

  private final CompanyRepository companies;
  private final ContactRepository contacts;
  private final LeadRepository leads;
  private final DealRepository deals;
  private final TaskRepository taskRows;
  private final TaskService tasks;
  private final ActivityService activities;
  private final PipelineStageService stages;
  private final AuditEventService audit;
  private final WorkspaceRepository workspaces;

  public DashboardService(
      CompanyRepository companies,
      ContactRepository contacts,
      LeadRepository leads,
      DealRepository deals,
      TaskRepository taskRows,
      TaskService tasks,
      ActivityService activities,
      PipelineStageService stages,
      AuditEventService audit,
      WorkspaceRepository workspaces) {
    this.companies = companies;
    this.contacts = contacts;
    this.leads = leads;
    this.deals = deals;
    this.taskRows = taskRows;
    this.tasks = tasks;
    this.activities = activities;
    this.stages = stages;
    this.audit = audit;
    this.workspaces = workspaces;
  }

  /** {@code from} and {@code to} as {@link Period#resolve} takes them (null: the default). */
  public DashboardResponse dashboard(LocalDate from, LocalDate to) {
    ZoneId zone =
        ZoneId.of(workspaces.findById(WorkspaceContext.workspaceId()).orElseThrow().getTimezone());
    Period period = Period.resolve(from, to, zone, Clock.systemUTC());
    // One instant for the overdue count and both task lists, so they agree.
    Instant now = Instant.now();

    // Open deals: per stage and currency, then per currency across the stages.
    List<Object[]> open = deals.openTotals();
    Map<String, BigDecimal> amounts = new TreeMap<>();
    Map<String, BigDecimal> weighted = new HashMap<>();
    long openDeals = 0;
    for (Object[] row : open) {
      String currency = (String) row[1];
      amounts.merge(currency, (BigDecimal) row[3], BigDecimal::add);
      weighted.merge(currency, (BigDecimal) row[4], BigDecimal::add);
      openDeals += (Long) row[2];
    }
    List<PipelineTotal> openPipeline =
        amounts.entrySet().stream()
            .map(
                e ->
                    new PipelineTotal(
                        e.getKey(),
                        e.getValue(),
                        // The sum of amount × percent, / 100 and rounded once per currency.
                        weighted
                            .get(e.getKey())
                            .movePointLeft(2)
                            .setScale(2, RoundingMode.HALF_UP)))
            .toList();

    // Deals closed in the period, by the kind of their current stage.
    long won = 0;
    long lost = 0;
    List<MoneyTotal> wonTotals = new ArrayList<>();
    for (Object[] row : deals.closedTotals(period.start(), period.endExclusive())) {
      long count = (Long) row[2];
      if (row[0] == StageKind.WON) {
        won += count;
        wonTotals.add(new MoneyTotal((String) row[1], (BigDecimal) row[3]));
      } else {
        lost += count;
      }
    }

    return new DashboardResponse(
        period.dto(),
        new Counts(
            companies.countByArchivedAtIsNull(),
            contacts.countByArchivedAtIsNull(),
            leads.countByArchivedAtIsNullAndStatusIn(LeadStatus.open()),
            openDeals,
            taskRows.countByStatusAndDueAtBefore(TaskStatus.OPEN, now)),
        openPipeline,
        new WonSummary(won, wonTotals),
        new LostSummary(lost),
        byStage(stages.list(), open),
        activities
            .list(
                new ActivityFilter(null, null, null, null, null, null, null, null),
                0,
                RECENT_ACTIVITIES)
            .getContent(),
        myOpenTasks(null, now, now),
        myOpenTasks(true, null, now),
        audit.recentRecordChanges(RECENT_CHANGES));
  }

  /**
   * Every open stage of {@code stages} in order, with its deals in {@code openTotals} (the rows of
   * {@link DealRepository#openTotals}, by currency, so each stage's totals come out sorted).
   */
  static List<StageBucket> byStage(List<StageResponse> stages, List<Object[]> openTotals) {
    Map<Long, Long> counts = new HashMap<>();
    Map<Long, List<MoneyTotal>> totals = new HashMap<>();
    for (Object[] row : openTotals) {
      long stageId = (Long) row[0];
      counts.merge(stageId, (Long) row[2], Long::sum);
      totals
          .computeIfAbsent(stageId, id -> new ArrayList<>())
          .add(new MoneyTotal((String) row[1], (BigDecimal) row[3]));
    }
    return stages.stream()
        .filter(s -> s.kind() == StageKind.OPEN)
        .map(
            s ->
                new StageBucket(
                    s, counts.getOrDefault(s.id(), 0L), totals.getOrDefault(s.id(), List.of())))
        .toList();
  }

  /**
   * The caller's open tasks, overdue or due from {@code dueFrom}, soonest (or longest ago) first.
   */
  private List<TaskResponse> myOpenTasks(Boolean overdue, Instant dueFrom, Instant now) {
    TaskFilter filter =
        new TaskFilter(
            null,
            List.of(TaskStatus.OPEN),
            null,
            false,
            true,
            overdue,
            dueFrom,
            null,
            null,
            null,
            null,
            null);
    return tasks.list(filter, now, 0, MY_TASKS, TaskSort.DUE_AT, Direction.ASC).getContent();
  }
}
