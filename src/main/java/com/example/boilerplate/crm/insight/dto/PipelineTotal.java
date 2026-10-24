package com.example.boilerplate.crm.insight.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The open deals in one currency: the sum of their amounts, and of each amount times its
 * probability (the deal's, else its stage's) / 100, rounded half up to cents once per currency.
 */
public record PipelineTotal(
    @NotNull String currency,
    @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "1250.00")
        BigDecimal amount,
    @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "625.00")
        BigDecimal weightedAmount) {}
