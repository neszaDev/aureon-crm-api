package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The open deals as of now, by stage (as on the dashboard) and by owner. */
public record PipelineReport(
    @NotNull List<StageBucket> byStage, @NotNull List<OwnerBucket> byOwner) {}
