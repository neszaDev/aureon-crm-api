package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Every month of the period, oldest first. */
public record WonLostReport(@NotNull List<MonthBucket> months) {}
