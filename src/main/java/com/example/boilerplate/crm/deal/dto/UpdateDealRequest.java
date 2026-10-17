package com.example.boilerplate.crm.deal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every editable field (an omitted optional one is cleared) and the version last read. No stage: a
 * move changes it.
 */
public record UpdateDealRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Digits(integer = 13, fraction = 2) @PositiveOrZero BigDecimal amount,
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
