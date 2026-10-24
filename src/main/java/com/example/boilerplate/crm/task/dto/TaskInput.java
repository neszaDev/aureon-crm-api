package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.common.Priority;
import java.time.Instant;

/**
 * The editable fields of a task, as create and update requests carry them (api.md {@code
 * TaskInput}). The service normalizes and applies them the same way for both.
 */
public interface TaskInput {
  String title();

  String description();

  /** An instant; null means no due time. */
  Instant dueAt();

  /** Null means MEDIUM. */
  Priority priority();

  /**
   * Create: null means the caller. Update: the assignee to keep, reassign to, or null to unassign.
   */
  Long assigneeId();

  Long companyId();

  Long contactId();

  Long leadId();

  Long dealId();
}
