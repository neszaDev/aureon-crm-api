package com.example.boilerplate.testresult.dto;

import com.example.boilerplate.testresult.TestStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record TestResultResponse(
    Long id,
    String testName,
    TestStatus status,
    BigDecimal score,
    Instant testedAt,
    String notes,
    Instant createdAt,
    Instant updatedAt) {}
