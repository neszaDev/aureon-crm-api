package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.audit.Derived;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.deal.DealStatus;
import com.example.boilerplate.workspace.dto.MemberRef;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A deal: the {@link DealSummary} fields and the rest (api.md {@code Deal}). */
public record DealResponse(
    long id,
    @NotNull String name,
    @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "1250.00")
        BigDecimal amount,
    @NotNull String currency,
    @NotNull StageRef stage,
    @Derived @NotNull Instant stageEnteredAt,
    @NotNull DealStatus status,
    @Derived int probability,
    LocalDate expectedCloseDate,
    MemberRef owner,
    Ref company,
    PersonRef contact,
    Instant closedAt,
    @Derived boolean archived,
    @NotNull Instant updatedAt,
    long version,
    Integer probabilityOverride,
    Ref source,
    String lostReason,
    PersonRef lead,
    String notes,
    Instant archivedAt,
    @NotNull Instant createdAt) {}
