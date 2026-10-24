package com.example.boilerplate.file.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record FileResponse(
    @NotNull Long id,
    @NotNull String name,
    @NotNull String contentType,
    long size,
    @NotNull Instant createdAt) {}
