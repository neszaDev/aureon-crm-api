package com.example.boilerplate.audit.dto;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/** A line of the audit trail. {@code actor} is absent when the event has none. */
public record AuditEventResponse(
    long id,
    @NotNull Instant occurredAt,
    AuditActor actor,
    @NotNull AuditAction action,
    @NotNull AuditEntityType entityType,
    long entityId,
    String entityLabel,
    @NotNull Map<String, Object> changes) {}
