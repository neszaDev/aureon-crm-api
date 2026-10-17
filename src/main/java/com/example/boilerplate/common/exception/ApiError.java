package com.example.boilerplate.common.exception;

import java.time.Instant;
import java.util.Map;

/** Every error body. {@code code} is left out of the JSON when an error has none (e.g. a 401). */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    ErrorCode code,
    String message,
    String path,
    Map<String, String> fieldErrors) {}
