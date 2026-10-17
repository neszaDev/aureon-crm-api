package com.example.boilerplate.crm.activity.dto;

import com.example.boilerplate.crm.activity.ActivityType;
import java.time.Instant;

/**
 * The list's query parameters besides paging; every one given must hold. {@code from} is inclusive
 * and {@code to} exclusive, both on {@code occurredAt}, so consecutive periods neither overlap nor
 * leave a gap.
 */
public record ActivityFilter(
    ActivityType type,
    Long companyId,
    Long contactId,
    Long leadId,
    Long dealId,
    Long authorId,
    Instant from,
    Instant to) {}
