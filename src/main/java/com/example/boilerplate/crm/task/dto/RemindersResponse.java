package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.lead.dto.LeadResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What needs the caller's attention, at most 20 of each, soonest first. */
public record RemindersResponse(
    @NotNull List<TaskResponse> overdueTasks,
    @NotNull List<TaskResponse> dueSoonTasks,
    @NotNull List<LeadResponse> followUpLeads) {}
