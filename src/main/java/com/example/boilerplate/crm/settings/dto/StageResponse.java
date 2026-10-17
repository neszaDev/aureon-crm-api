package com.example.boilerplate.crm.settings.dto;

import com.example.boilerplate.crm.settings.StageKind;

// dealCount arrives with deals (B2).
public record StageResponse(
    long id, String name, int position, int probability, StageKind kind, long version) {}
