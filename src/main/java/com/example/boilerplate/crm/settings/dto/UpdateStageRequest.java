package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.*;

public record UpdateStageRequest(
    @NotBlank @Size(max = 60) String name,
    @Min(0) @Max(100) int probability,
    @NotNull Long version) {}
