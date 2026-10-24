package com.example.boilerplate.crm.activity.dto;

import com.example.boilerplate.crm.activity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Every editable field (an omitted optional one is cleared) and the version last read. */
public record UpdateActivityRequest(
    @NotNull ActivityType type,
    @NotBlank @Size(max = 200) String subject,
    @Size(max = 5000) String body,
    Instant occurredAt,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId,
    @NotNull Long version)
    implements ActivityInput {}
