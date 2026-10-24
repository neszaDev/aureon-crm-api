package com.example.boilerplate.crm.common;

import jakarta.validation.constraints.NotNull;

/** A contact or lead referenced by another record. */
public record PersonRef(long id, @NotNull String fullName) {}
