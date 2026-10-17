package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.*;

public record CreateLeadSourceRequest(@NotBlank @Size(max = 60) String name) {}
