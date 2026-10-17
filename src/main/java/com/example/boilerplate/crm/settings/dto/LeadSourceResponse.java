package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.NotNull;

public record LeadSourceResponse(long id, @NotNull String name, boolean archived, long version) {}
