package com.example.boilerplate.crm.task.dto;

import com.example.boilerplate.crm.lead.dto.LeadResponse;
import java.util.List;

/** What needs the caller's attention, at most 20 of each, soonest first. */
public record RemindersResponse(
    List<TaskResponse> overdueTasks,
    List<TaskResponse> dueSoonTasks,
    List<LeadResponse> followUpLeads) {}
