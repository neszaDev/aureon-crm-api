package com.example.boilerplate.crm.deal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every editable field (an omitted optional one is cleared) and the version last read. No stage: a
 * move changes it.
 */
public record UpdateDealRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull
        @Digits(integer = 13, fraction = 2)
        @PositiveOrZero
        @Schema(type = "string", example = "1250.00")
        BigDecimal amount,
    @Size(max = 3) String currency,
    @Min(0) @Max(100) Integer probability,
    LocalDate expectedCloseDate,
    Long ownerId,
    Long companyId,
    Long contactId,
    Long sourceId,
    @Size(max = 5000) String notes,
    @NotNull Long version)
    implements DealInput {}
