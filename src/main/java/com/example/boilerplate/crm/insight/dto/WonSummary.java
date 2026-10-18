package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.common.MoneyTotal;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Active deals in the WON stage closed in the period, and their amounts per currency. */
public record WonSummary(long count, @NotNull List<MoneyTotal> totals) {}
