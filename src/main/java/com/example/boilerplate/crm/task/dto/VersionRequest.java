package com.example.boilerplate.crm.task.dto;

import jakarta.validation.constraints.NotNull;

/** The body of a task action (complete, reopen, cancel): the version the client last read. */
public record VersionRequest(@NotNull Long version) {}
