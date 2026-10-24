package com.example.boilerplate.common.exception;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/** Every error body. {@code code} is left out of the JSON when an error has none (e.g. a 401). */
public record ApiError(
    @NotNull Instant timestamp,
    int status,
    @NotNull String error,
    ErrorCode code,
    @NotNull String message,
    @NotNull String path,
    @NotNull Map<String, String> fieldErrors) {}
