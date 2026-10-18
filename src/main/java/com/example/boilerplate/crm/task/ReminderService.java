package com.example.boilerplate.crm.task;

import com.example.boilerplate.crm.lead.LeadService;
import com.example.boilerplate.crm.lead.LeadSort;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.crm.lead.dto.LeadFilter;
import com.example.boilerplate.crm.task.dto.RemindersResponse;
import com.example.boilerplate.crm.task.dto.TaskFilter;
import com.example.boilerplate.crm.task.dto.TaskResponse;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What needs the caller's attention: their open tasks that are overdue or due within 24 hours, and
 * their open leads whose follow-up date has come. Nothing is sent anywhere; the lists are read on
 * request. Tasks are measured in instants; only the follow-up date, a calendar date, needs the
 * workspace's time zone to know what today is.
 */
@Service
@Transactional(readOnly = true)
public class ReminderService {
  private static final int LIMIT = 20;
  private static final Duration SOON = Duration.ofHours(24);

  private final TaskService tasks;
  private final LeadService leads;
  private final WorkspaceRepository workspaces;

  public ReminderService(TaskService tasks, LeadService leads, WorkspaceRepository workspaces) {
    this.tasks = tasks;
    this.leads = leads;
    this.workspaces = workspaces;
  }

  public RemindersResponse reminders() {
    long caller = WorkspaceContext.require().userId();
    // One instant for both lists: a task due between two readings of the clock isn't in neither.
    Instant now = Instant.now();
    List<TaskResponse> overdue =
        tasks
            .list(openTasks(true, null, null), now, 0, LIMIT, TaskSort.DUE_AT, Direction.ASC)
            .getContent();
    List<TaskResponse> dueSoon =
        tasks
            .list(
                openTasks(null, now, now.plus(SOON)), now, 0, LIMIT, TaskSort.DUE_AT, Direction.ASC)
            .getContent();
    LocalDate today =
        LocalDate.now(
            ZoneId.of(
                workspaces.findById(WorkspaceContext.workspaceId()).orElseThrow().getTimezone()));
    return new RemindersResponse(
        overdue,
        dueSoon,
        leads
            .list(
                new LeadFilter(null, LeadStatus.open(), caller, false, null, null, today, false),
                0,
                LIMIT,
                LeadSort.FOLLOW_UP_DATE,
                Direction.ASC)
            .getContent());
  }

  /** The caller's open tasks, overdue and due between the instants as given. */
  private static TaskFilter openTasks(Boolean overdue, Instant dueFrom, Instant dueTo) {
    return new TaskFilter(
        null,
        List.of(TaskStatus.OPEN),
        null,
        false,
        true,
        overdue,
        dueFrom,
        dueTo,
        null,
        null,
        null,
        null);
  }
}
