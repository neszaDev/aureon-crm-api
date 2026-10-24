package com.example.boilerplate.crm.settings.dto;

import com.example.boilerplate.audit.Derived;
import com.example.boilerplate.crm.settings.StageKind;
import jakarta.validation.constraints.NotNull;

/** A pipeline stage; {@code dealCount} counts its active deals. */
public record StageResponse(
    long id,
    @NotNull String name,
    int position,
    int probability,
    @NotNull StageKind kind,
    @Derived long dealCount,
    long version) {}
