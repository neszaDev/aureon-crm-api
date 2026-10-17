package com.example.boilerplate.crm.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** A sum in one currency; totals are lists of these, never a sum across currencies. */
public record MoneyTotal(
    @NotNull String currency,
    @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "1250.00")
        BigDecimal amount) {}
