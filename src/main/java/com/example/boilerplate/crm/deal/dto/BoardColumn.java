package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.crm.settings.dto.StageResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A stage of the board. {@code count} and {@code totals} (by currency) cover every matching deal in
 * the stage; {@code deals} lists the most recently updated of them, at most 50.
 */
public record BoardColumn(
    @NotNull StageResponse stage,
    long count,
    @NotNull List<MoneyTotal> totals,
    @NotNull List<DealSummary> deals) {}
