package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.common.MoneyTotal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The active deals won and lost in one month of the workspace's zone, within the period. */
public record MonthBucket(
    @NotNull @Schema(example = "2026-09") String month,
    long won,
    long lost,
    @NotNull List<MoneyTotal> wonTotals) {}
