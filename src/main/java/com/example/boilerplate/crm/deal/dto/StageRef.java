package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.crm.settings.StageKind;
import jakarta.validation.constraints.NotNull;

/** The stage a deal is in. */
public record StageRef(long id, @NotNull String name, @NotNull StageKind kind) {}
