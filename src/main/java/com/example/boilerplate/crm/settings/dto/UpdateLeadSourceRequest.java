package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.*;

public record UpdateLeadSourceRequest(
    @NotBlank @Size(max = 60) String name, boolean archived, @NotNull Long version) {}
