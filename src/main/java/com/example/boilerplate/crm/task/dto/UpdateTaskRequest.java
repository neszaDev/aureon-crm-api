package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.common.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Every editable field (an omitted optional one is cleared) and the version last read. */
public record UpdateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    Instant dueAt,
    Priority priority,
    Long assigneeId,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId,
    @NotNull Long version)
    implements TaskInput {}
