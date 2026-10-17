package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.common.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    Instant dueAt,
    Priority priority,
    Long assigneeId,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId)
    implements TaskInput {}
