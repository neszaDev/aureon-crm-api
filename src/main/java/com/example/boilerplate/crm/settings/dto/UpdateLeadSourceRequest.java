package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.*;

public record UpdateLeadSourceRequest(
    @NotBlank @Size(max = 60) String name, @NotNull Boolean archived, @NotNull Long version) {}
