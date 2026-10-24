package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.audit.dto.AuditEventResponse;
import com.example.boilerplate.crm.activity.dto.ActivityResponse;
import com.example.boilerplate.crm.task.dto.TaskResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The workspace at a glance (api.md, Dashboard). {@code won} and {@code lost} cover the period,
 * everything else is as of now; {@code byStage} lists every open stage in order; the task lists are
 * the caller's; {@code recentChanges} are the newest audit events on records.
 */
public record DashboardResponse(
    @NotNull PeriodDto period,
    @NotNull Counts counts,
    @NotNull List<PipelineTotal> openPipeline,
    @NotNull WonSummary won,
    @NotNull LostSummary lost,
    @NotNull List<StageBucket> byStage,
    @NotNull List<ActivityResponse> recentActivities,
    @NotNull List<TaskResponse> myUpcomingTasks,
    @NotNull List<TaskResponse> myOverdueTasks,
    @NotNull List<AuditEventResponse> recentChanges) {}
