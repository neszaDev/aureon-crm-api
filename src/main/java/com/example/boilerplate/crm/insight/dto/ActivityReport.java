package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What each member did in the period (the one in {@code period}), most in total first. */
public record ActivityReport(@NotNull PeriodDto period, @NotNull List<UserActivity> byUser) {}
