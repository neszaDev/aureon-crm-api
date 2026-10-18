package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.settings.dto.StageResponse;
import jakarta.validation.constraints.NotNull;

/** An open stage, or Won, and how many of the created deals reached it or a later one. */
public record FunnelStep(@NotNull StageResponse stage, long reached) {}
