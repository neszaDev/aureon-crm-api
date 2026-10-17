package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.task.TaskStatus;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/**
 * A task (api.md {@code Task}), in the list and everywhere else. {@code overdue} is computed: open
 * with a due time in the past.
 */
public record TaskResponse(
    long id,
    String title,
    String description,
    Instant dueAt,
    Priority priority,
    MemberRef assignee,
    MemberRef createdBy,
    TaskStatus status,
    boolean overdue,
    Instant completedAt,
    Ref company,
    PersonRef contact,
    PersonRef lead,
    Ref deal,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
