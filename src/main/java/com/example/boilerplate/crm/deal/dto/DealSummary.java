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

/**
 * A row of the deal list and a card of the board. {@code status} is the stage's kind; {@code
 * probability} the deal's own, or else its stage's.
 */
public record DealSummary(
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
    long version) {}
