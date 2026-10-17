package com.example.boilerplate.crm.task;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.LinkRefs;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.common.RecordLinks;
import com.example.boilerplate.crm.task.dto.*;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current workspace's tasks. Tasks are cancelled, never deleted. Whoever may edit one (its
 * assignee, its creator, or anyone with {@code RECORDS_EDIT_ANY}) may also complete, reopen and
 * cancel it.
 */
@Service
@Transactional
public class TaskService {
  private final TaskRepository tasks;
  private final LinkRefs linkRefs;
  private final RecordLinks links;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public TaskService(
      TaskRepository tasks,
      LinkRefs linkRefs,
      RecordLinks links,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.tasks = tasks;
    this.linkRefs = linkRefs;
    this.links = links;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  @Transactional(readOnly = true)
  public Page<TaskResponse> list(
      TaskFilter filter, int page, int size, TaskSort sort, Direction direction) {
    return list(filter, Instant.now(), page, size, sort, direction);
  }

  /**
   * As above, with overdue measured against {@code now}: the reminders ask for several lists that
   * must not leave a gap between them.
   */
  @Transactional(readOnly = true)
  Page<TaskResponse> list(
      TaskFilter filter, Instant now, int page, int size, TaskSort sort, Direction direction) {
    Specification<CrmTask> matching =
        TaskSpecifications.matching(filter, WorkspaceContext.require().userId(), now);
    Specification<CrmTask> ordering = TaskSpecifications.ordering(sort, direction);
    Page<CrmTask> found =
        ordering == null
            ? tasks.findAll(matching, ListParams.pageable(page, size, direction, sort))
            : tasks.findAll(matching.and(ordering), ListParams.unsorted(page, size));
    Related related = related(found.getContent());
    return found.map(t -> response(t, related, now));
  }

  @Transactional(readOnly = true)
  public TaskResponse get(long id) {
    return response(task(id));
  }

  public TaskResponse create(CreateTaskRequest r) {
    long assignee = access.ownerForCreate("assigneeId", r.assigneeId());
    checkLinks(r);
    CrmTask task = new CrmTask(WorkspaceContext.require().userId());
    edit(task, r);
    task.changeAssignee(assignee);
    tasks.save(task);
    audit.record(AuditAction.CREATE, AuditEntityType.TASK, task.getId(), task.getTitle(), Map.of());
    return response(task);
  }

  public TaskResponse update(long id, UpdateTaskRequest r) {
    CrmTask task = task(id);
    requireEditable(task);
    Versions.check(task, r.version());
    Long assignee = access.ownerForUpdate("assigneeId", r.assigneeId(), task.getAssigneeId());
    checkLinks(r);
    // One clock reading for both snapshots: the diff must not show `overdue` flipping by itself.
    Instant now = Instant.now();
    TaskResponse before = response(task, now);
    edit(task, r);
    task.changeAssignee(assignee);
    // Flush so the response carries the new version.
    TaskResponse after = response(tasks.saveAndFlush(task), now);
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.TASK, id, task.getTitle(), changes);
    }
    return after;
  }

  public TaskResponse complete(long id, VersionRequest r) {
    return move(
        id, r, TaskStatus.COMPLETED, AuditAction.COMPLETE, "Only open tasks can be completed");
  }

  public TaskResponse reopen(long id, VersionRequest r) {
    return move(
        id,
        r,
        TaskStatus.OPEN,
        AuditAction.REOPEN,
        "Only completed or cancelled tasks can be reopened");
  }

  public TaskResponse cancel(long id, VersionRequest r) {
    return move(
        id, r, TaskStatus.CANCELLED, AuditAction.CANCEL, "Only open tasks can be cancelled");
  }

  /**
   * Moves the task to {@code target} when {@link TaskStatus#canMoveTo allows it}, else a 409 with
   * {@code refusal}; the version is compared first, since a stale client has not seen the status.
   */
  private TaskResponse move(
      long id, VersionRequest r, TaskStatus target, AuditAction action, String refusal) {
    CrmTask task = task(id);
    requireEditable(task);
    Versions.check(task, r.version());
    if (!task.getStatus().canMoveTo(target)) throw new ConflictException(refusal);
    Instant now = Instant.now();
    TaskResponse before = response(task, now);
    task.changeStatus(target);
    TaskResponse after = response(tasks.saveAndFlush(task), now);
    audit.record(
        action, AuditEntityType.TASK, id, task.getTitle(), auditChanges.recordDiff(before, after));
    return after;
  }

  private CrmTask task(long id) {
    return tasks.findById(id).orElseThrow(() -> new NotFoundException("Task not found"));
  }

  private void requireEditable(CrmTask task) {
    access.requireEditableBy(task.getAssigneeId(), task.getCreatedBy());
  }

  private void checkLinks(TaskInput r) {
    links.company(r.companyId());
    links.contact(r.contactId());
    links.lead(r.leadId());
    links.deal(r.dealId());
  }

  /**
   * Create and update apply the request alike; the assignee is decided and the links are checked by
   * the caller.
   */
  private static void edit(CrmTask task, TaskInput r) {
    task.update(
        Texts.required("title", r.title()),
        Texts.optional(r.description()),
        r.dueAt(),
        r.priority() == null ? Priority.MEDIUM : r.priority(),
        r.companyId(),
        r.contactId(),
        r.leadId(),
        r.dealId());
  }

  /** What a page shows besides the tasks' own columns: one query each for a whole page. */
  private record Related(Map<Long, MemberRef> people, LinkRefs.Refs links) {
    MemberRef assignee(CrmTask t) {
      return t.getAssigneeId() == null ? null : people.get(t.getAssigneeId());
    }

    MemberRef createdBy(CrmTask t) {
      return t.getCreatedBy() == null ? null : people.get(t.getCreatedBy());
    }
  }

  private Related related(List<CrmTask> page) {
    List<Long> people = new ArrayList<>(column(page, CrmTask::getAssigneeId));
    people.addAll(column(page, CrmTask::getCreatedBy));
    return new Related(
        directory.refs(people),
        linkRefs.of(
            column(page, CrmTask::getCompanyId),
            column(page, CrmTask::getContactId),
            column(page, CrmTask::getLeadId),
            column(page, CrmTask::getDealId)));
  }

  /** One column of the page, null (no link) and repeats included: the callers filter them. */
  private static List<Long> column(List<CrmTask> page, Function<CrmTask, Long> field) {
    return page.stream().map(field).toList();
  }

  private TaskResponse response(CrmTask t) {
    return response(t, Instant.now());
  }

  private TaskResponse response(CrmTask t, Instant now) {
    return response(t, related(List.of(t)), now);
  }

  private static TaskResponse response(CrmTask t, Related r, Instant now) {
    return new TaskResponse(
        t.getId(),
        t.getTitle(),
        t.getDescription(),
        t.getDueAt(),
        t.getPriority(),
        r.assignee(t),
        r.createdBy(t),
        t.getStatus(),
        t.isOverdue(now),
        t.getCompletedAt(),
        r.links().company(t.getCompanyId()),
        r.links().contact(t.getContactId()),
        r.links().lead(t.getLeadId()),
        r.links().deal(t.getDealId()),
        t.getCreatedAt(),
        t.getUpdatedAt(),
        t.getVersion());
  }
}
