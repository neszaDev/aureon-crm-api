package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.crm.task.TaskStatus;
import java.time.Instant;
import java.util.List;

/**
 * The list's query parameters besides paging and sorting. {@code q}: the title contains it (any
 * case); {@code statuses}: any of them (null or empty: all); {@code mine}: assigned to the caller;
 * {@code unassigned}: no assignee (not with {@code assigneeId} or {@code mine}: 400); {@code
 * overdue}: true for open tasks due in the past, false for all the others, null for both; {@code
 * dueFrom}/{@code dueTo}: the due time from {@code dueFrom} (inclusive) to {@code dueTo}
 * (exclusive), so tasks without one never match.
 */
public record TaskFilter(
    String q,
    List<TaskStatus> statuses,
    Long assigneeId,
    boolean unassigned,
    boolean mine,
    Boolean overdue,
    Instant dueFrom,
    Instant dueTo,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId) {
  public TaskFilter {
    if (unassigned && (assigneeId != null || mine)) {
      throw new FieldValidationException("unassigned", "can't be combined with an assignee");
    }
  }
}
