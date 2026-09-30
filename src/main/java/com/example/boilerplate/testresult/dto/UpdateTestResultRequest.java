package com.example.boilerplate.testresult.dto;

import com.example.boilerplate.testresult.TestStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateTestResultRequest(
    @NotBlank @Size(max = 100) String testName,
    @NotNull TestStatus status,
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal score,
    @NotNull Instant testedAt,
    @Size(max = 1000) String notes) {}
