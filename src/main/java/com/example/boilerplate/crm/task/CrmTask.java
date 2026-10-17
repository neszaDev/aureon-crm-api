package com.example.boilerplate.crm.task;

import com.example.boilerplate.common.entity.WorkspaceOwnedEntity;
import com.example.boilerplate.crm.common.Priority;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A follow-up assigned to a member, optionally about a company, contact, lead or deal. */
@Entity
@Table(name = "tasks")
public class CrmTask extends WorkspaceOwnedEntity {
  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 5000)
  private String description;

  private Instant dueAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TaskStatus status = TaskStatus.OPEN;

  /** A member's user id, null once they left the workspace. */
  private Long assigneeId;

  /**
   * A member's user id, null once they left the workspace: the database clears it, so it is never
   * written after the insert.
   */
  @Column(updatable = false)
  private Long createdBy;

  private Instant completedAt;

  private Long companyId;

  private Long contactId;

  private Long leadId;

  private Long dealId;

  protected CrmTask() {}

  /** A new open task by the member {@code createdBy}; {@link #update} fills it. */
  public CrmTask(long createdBy) {
    this.createdBy = createdBy;
  }

  /**
   * Every editable field but the assignee ({@link #changeAssignee}); null clears an optional one.
   * The links are already checked. The due time is kept in microseconds, as stored, so a response
   * equals a later read and an unchanged time is no change.
   */
  public void update(
      String title,
      String description,
      Instant dueAt,
      Priority priority,
      Long companyId,
      Long contactId,
      Long leadId,
      Long dealId) {
    this.title = title;
    this.description = description;
    this.dueAt = dueAt == null ? null : dueAt.truncatedTo(ChronoUnit.MICROS);
    this.priority = priority;
    this.companyId = companyId;
    this.contactId = contactId;
    this.leadId = leadId;
    this.dealId = dealId;
  }

  /** The caller has already checked who may assign; null leaves the task unassigned. */
  public void changeAssignee(Long assigneeId) {
    this.assigneeId = assigneeId;
  }

  /**
   * The caller has checked {@link TaskStatus#canMoveTo}. Completing records the time (in
   * microseconds, as stored); any other status forgets it.
   */
  public void changeStatus(TaskStatus target) {
    status = target;
    completedAt =
        target == TaskStatus.COMPLETED ? Instant.now().truncatedTo(ChronoUnit.MICROS) : null;
  }

  /** Open with a due time before {@code now}; a finished task or one without a due time isn't. */
  public boolean isOverdue(Instant now) {
    return status == TaskStatus.OPEN && dueAt != null && dueAt.isBefore(now);
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public Priority getPriority() {
    return priority;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public Long getContactId() {
    return contactId;
  }

  public Long getLeadId() {
    return leadId;
  }

  public Long getDealId() {
    return dealId;
  }
}
