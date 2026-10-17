package com.example.boilerplate.testresult.dto;

import com.example.boilerplate.testresult.TestStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record TestResultResponse(
    @NotNull Long id,
    @NotNull String testName,
    @NotNull TestStatus status,
    @NotNull BigDecimal score,
    @NotNull Instant testedAt,
    String notes,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
