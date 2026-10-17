package com.example.boilerplate.crm.company.dto;

import jakarta.validation.constraints.NotNull;

/** A possible duplicate of a company being entered. */
public record CompanyRef(long id, @NotNull String name, String website) {}
