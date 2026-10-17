package com.example.boilerplate.crm.common;

import jakarta.validation.constraints.NotNull;

/** A record referenced by another (company, stage, source): enough to show a link. */
public record Ref(long id, @NotNull String name) {}
