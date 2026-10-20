package com.example.boilerplate.crm.insight;

import com.example.boilerplate.crm.activity.ActivityRepository;
import com.example.boilerplate.crm.activity.ActivityType;
import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.deal.DealStageChangeRepository;
import com.example.boilerplate.crm.insight.dto.*;
import com.example.boilerplate.crm.lead.LeadService;
import com.example.boilerplate.crm.lead.LeadSort;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.crm.lead.dto.LeadFilter;
import com.example.boilerplate.crm.lead.dto.LeadResponse;
import com.example.boilerplate.crm.settings.PipelineStageService;
import com.example.boilerplate.crm.settings.StageKind;
import com.example.boilerplate.crm.task.TaskRepository;
import com.example.boilerplate.crm.task.TaskService;
import com.example.boilerplate.crm.task.TaskSort;
import com.example.boilerplate.crm.task.TaskStatus;
import com.example.boilerplate.crm.task.dto.TaskFilter;
import com.example.boilerplate.crm.task.dto.TaskResponse;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reports of the current workspace (api.md, Dashboard and reports), computed from the stored
 * data on every request. Tenant-filtered JPQL, except the funnel ({@link ReportQueries}).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {
  static final int FIRST = 20;
  static final Duration UPCOMING = Duration.ofDays(7);
  private static final Comparator<MemberRef> BY_EMAIL =
      Comparator.nullsLast(Comparator.comparing(MemberRef::email));

  private final DealRepository deals;
  private final DealStageChangeRepository history;
  private final ActivityRepository activities;
  private final TaskRepository taskRows;
  private final TaskService tasks;
  private final LeadService leads;
  private final PipelineStageService stages;
  private final MemberDirectory members;
  private final WorkspaceRepository workspaces;
  private final ReportQueries queries;
  private final Clock clock;

  public ReportService(
      DealRepository deals,
      DealStageChangeRepository history,
      ActivityRepository activities,
      TaskRepository taskRows,
      TaskService tasks,
      LeadService leads,
      PipelineStageService stages,
      MemberDirectory members,
      WorkspaceRepository workspaces,
      ReportQueries queries,
      Clock clock) {
    this.deals = deals;
    this.history = history;
    this.activities = activities;
    this.taskRows = taskRows;
    this.tasks = tasks;
    this.leads = leads;
    this.stages = stages;
    this.members = members;
    this.workspaces = workspaces;
    this.queries = queries;
    this.clock = clock;
  }

  /** By owner: the largest single-currency total first, then by email, unassigned last. */
  public PipelineReport pipeline() {
    Map<Long, Long> counts = new HashMap<>();
    Map<Long, List<MoneyTotal>> totals = new HashMap<>();
    // By currency, so each owner's totals come out sorted. Null: unassigned.
    for (Object[] row : deals.openTotalsByOwner()) {
      Long ownerId = (Long) row[0];
      counts.merge(ownerId, (Long) row[2], Long::sum);
      totals
          .computeIfAbsent(ownerId, id -> new ArrayList<>())
          .add(new MoneyTotal((String) row[1], (BigDecimal) row[3]));
    }
    Map<Long, MemberRef> refs = members.refs(totals.keySet());
    Comparator<OwnerBucket> largest =
        Comparator.comparing(
            b -> b.totals().stream().map(MoneyTotal::amount).max(Comparator.naturalOrder()).get());
    List<OwnerBucket> byOwner =
        totals.entrySet().stream()
            .map(
                e ->
                    new OwnerBucket(
                        e.getKey() == null ? null : refs.get(e.getKey()),
                        counts.get(e.getKey()),
                        e.getValue()))
            .sorted(largest.reversed().thenComparing(OwnerBucket::owner, BY_EMAIL))
            .toList();
    return new PipelineReport(DashboardService.byStage(stages.list(), deals.openTotals()), byOwner);
  }

  /** Every month from {@code from}'s to {@code to}'s, each cut to the period. */
  public WonLostReport wonLost(LocalDate from, LocalDate to) {
    Period period = period(from, to);
    List<MonthBucket> months = new ArrayList<>();
    for (YearMonth month = YearMonth.from(period.from());
        !month.isAfter(YearMonth.from(period.to()));
        month = month.plusMonths(1)) {
      LocalDate first = month.atDay(1);
      LocalDate last = month.atEndOfMonth();
      Period days =
          new Period(
              first.isBefore(period.from()) ? period.from() : first,
              last.isAfter(period.to()) ? period.to() : last,
              period.zone());
      // The dashboard's won/lost query, so the two never disagree.
      long won = 0;
      long lost = 0;
      List<MoneyTotal> wonTotals = new ArrayList<>();
      for (Object[] row : deals.closedTotals(days.start(), days.endExclusive())) {
        long count = (Long) row[2];
        if (row[0] == StageKind.WON) {
          won += count;
          wonTotals.add(new MoneyTotal((String) row[1], (BigDecimal) row[3]));
        } else {
          lost += count;
        }
      }
      months.add(new MonthBucket(month.toString(), won, lost, wonTotals));
    }
    return new WonLostReport(period.dto(), months);
  }

  /** The open stages in order, then Won, with the cohort's deals that reached each. */
  public FunnelReport funnel(LocalDate from, LocalDate to) {
    Period period = period(from, to);
    Map<Integer, Long> byTop =
        queries.funnel(WorkspaceContext.workspaceId(), period.start(), period.endExclusive());
    List<FunnelStep> steps =
        stages.list().stream()
            .filter(s -> s.kind() != StageKind.LOST)
            .map(
                s ->
                    new FunnelStep(
                        s,
                        byTop.entrySet().stream()
                            .filter(e -> e.getKey() != null && e.getKey() >= s.position())
                            .mapToLong(Map.Entry::getValue)
                            .sum()))
            .toList();
    Instant first = history.firstChangedAt();
    return new FunnelReport(
        period.dto(),
        byTop.values().stream().mapToLong(Long::longValue).sum(),
        first == null ? null : LocalDate.ofInstant(first, period.zone()),
        steps);
  }

  /** By member, most in total first, then by email, removed members last. */
  public ActivityReport activities(LocalDate from, LocalDate to) {
    Period period = period(from, to);
    // calls, emails, meetings, notes, tasks completed; by user id, null: no longer a member.
    Map<Long, long[]> byUser = new HashMap<>();
    for (Object[] row : activities.countByAuthorAndType(period.start(), period.endExclusive())) {
      int i =
          switch ((ActivityType) row[1]) {
            case CALL -> 0;
            case EMAIL -> 1;
            case MEETING -> 2;
            case NOTE -> 3;
          };
      byUser.computeIfAbsent((Long) row[0], id -> new long[5])[i] += (Long) row[2];
    }
    for (Object[] row : taskRows.countCompletedByAssignee(period.start(), period.endExclusive())) {
      byUser.computeIfAbsent((Long) row[0], id -> new long[5])[4] += (Long) row[1];
    }
    Map<Long, MemberRef> refs = members.refs(byUser.keySet());
    return new ActivityReport(
        period.dto(),
        byUser.entrySet().stream()
            .map(
                e -> {
                  long[] n = e.getValue();
                  MemberRef user = e.getKey() == null ? null : refs.get(e.getKey());
                  return new UserActivity(
                      user, n[0], n[1], n[2], n[3], n[4], Arrays.stream(n).sum());
                })
            .sorted(
                Comparator.comparingLong(UserActivity::total)
                    .reversed()
                    .thenComparing(UserActivity::user, BY_EMAIL))
            .toList());
  }

  /**
   * Workspace-wide: overdue tasks longest overdue first, tasks due from now to 7 days later soonest
   * first, open leads due by today in the workspace's zone oldest date first.
   */
  public FollowUpReport followUps() {
    // One instant for both task lists, so they neither overlap nor leave a gap.
    Instant now = clock.instant();
    Page<TaskResponse> overdue = openTasks(true, null, null, now);
    Page<TaskResponse> upcoming = openTasks(null, now, now.plus(UPCOMING), now);
    Page<LeadResponse> due =
        leads.list(
            new LeadFilter(
                null,
                LeadStatus.open(),
                null,
                false,
                null,
                null,
                LocalDate.ofInstant(now, zone()),
                false),
            0,
            FIRST,
            LeadSort.FOLLOW_UP_DATE,
            Direction.ASC);
    return new FollowUpReport(
        new CountedTasks(overdue.getTotalElements(), overdue.getContent()),
        new CountedTasks(upcoming.getTotalElements(), upcoming.getContent()),
        new CountedLeads(due.getTotalElements(), due.getContent()));
  }

  private Page<TaskResponse> openTasks(
      Boolean overdue, Instant dueFrom, Instant dueTo, Instant now) {
    TaskFilter filter =
        new TaskFilter(
            null,
            List.of(TaskStatus.OPEN),
            null,
            false,
            false,
            overdue,
            dueFrom,
            dueTo,
            null,
            null,
            null,
            null);
    return tasks.list(filter, now, 0, FIRST, TaskSort.DUE_AT, Direction.ASC);
  }

  /** {@code from} and {@code to} as {@link Period#resolve} takes them (null: the default). */
  private Period period(LocalDate from, LocalDate to) {
    return Period.resolve(from, to, zone(), clock.instant());
  }

  private ZoneId zone() {
    return ZoneId.of(
        workspaces.findById(WorkspaceContext.workspaceId()).orElseThrow().getTimezone());
  }
}
