package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Every month of the period, oldest first; {@code period} is the one used (the default's too). */
public record WonLostReport(@NotNull PeriodDto period, @NotNull List<MonthBucket> months) {}
