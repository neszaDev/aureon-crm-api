package com.example.boilerplate.crm.activity.dto;

import com.example.boilerplate.crm.activity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateActivityRequest(
    @NotNull ActivityType type,
    @NotBlank @Size(max = 200) String subject,
    @Size(max = 5000) String body,
    Instant occurredAt,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId)
    implements ActivityInput {}
