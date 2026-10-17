package com.example.boilerplate.crm.deal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code stageId}: an open stage; null means the open stage with the lowest position. */
public record CreateDealRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Digits(integer = 13, fraction = 2) @PositiveOrZero BigDecimal amount,
    @Size(max = 3) String currency,
    Long stageId,
    @Min(0) @Max(100) Integer probability,
    LocalDate expectedCloseDate,
    Long ownerId,
    Long companyId,
    Long contactId,
    Long sourceId,
    @Size(max = 5000) String notes)
    implements DealInput {}
