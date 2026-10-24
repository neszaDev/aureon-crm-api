package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.*;

public record CreateStageRequest(
    @NotBlank @Size(max = 60) String name, @NotNull @Min(0) @Max(100) Integer probability) {}
