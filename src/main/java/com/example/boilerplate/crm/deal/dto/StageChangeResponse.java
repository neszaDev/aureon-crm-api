package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * An entry of a deal's stage history. {@code fromStage} is null for the first one; {@code
 * changedBy} once that person is no longer a member.
 */
public record StageChangeResponse(
    HistoryStageRef fromStage,
    @NotNull HistoryStageRef toStage,
    MemberRef changedBy,
    @NotNull Instant changedAt) {}
