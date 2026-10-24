package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.crm.settings.dto.StageResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** A stage's active deals: how many and their amounts per currency (by code). */
public record StageBucket(
    @NotNull StageResponse stage, long count, @NotNull List<MoneyTotal> totals) {}
