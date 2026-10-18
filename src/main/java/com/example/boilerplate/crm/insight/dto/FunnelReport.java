package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * The active deals created in the period and how far their history went; {@code historyFrom} is the
 * day of the workspace's earliest stage change (null without any).
 */
public record FunnelReport(long created, LocalDate historyFrom, @NotNull List<FunnelStep> stages) {}
