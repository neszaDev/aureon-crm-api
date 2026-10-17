package com.example.boilerplate.workspace.dto;

import jakarta.validation.constraints.*;

public record UpdateWorkspaceRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
    @NotBlank @Size(max = 64) String timezone,
    @NotNull Long version) {}
