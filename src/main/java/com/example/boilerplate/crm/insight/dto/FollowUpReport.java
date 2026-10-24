package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;

/**
 * As of now: open tasks due in the past, open tasks due in the next 7 days, and open leads whose
 * follow-up date has come in the workspace's zone.
 */
public record FollowUpReport(
    @NotNull CountedTasks overdueTasks,
    @NotNull CountedTasks upcomingTasks,
    @NotNull CountedLeads dueLeads) {}
