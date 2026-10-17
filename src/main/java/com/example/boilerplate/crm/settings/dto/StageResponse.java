package com.example.boilerplate.crm.settings.dto;

import com.example.boilerplate.crm.settings.StageKind;

/** A pipeline stage; {@code dealCount} counts its active deals. */
public record StageResponse(
    long id,
    String name,
    int position,
    int probability,
    StageKind kind,
    long dealCount,
    long version) {}
