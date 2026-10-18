package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** The period a dashboard or report covers: whole days, both included, in the workspace's zone. */
public record PeriodDto(@NotNull LocalDate from, @NotNull LocalDate to) {}
