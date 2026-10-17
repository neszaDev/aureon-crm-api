package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.deal.DealStatus;
import com.example.boilerplate.workspace.dto.MemberRef;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A deal: the {@link DealSummary} fields and the rest (api.md {@code Deal}). */
public record DealResponse(
    long id,
    String name,
    @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", example = "1250.00")
        BigDecimal amount,
    String currency,
    StageRef stage,
    DealStatus status,
    int probability,
    LocalDate expectedCloseDate,
    MemberRef owner,
    Ref company,
    PersonRef contact,
    Instant closedAt,
    boolean archived,
    Instant updatedAt,
    long version,
    Integer probabilityOverride,
    Ref source,
    String lostReason,
    Ref lead,
    String notes,
    Instant archivedAt,
    Instant createdAt) {}
