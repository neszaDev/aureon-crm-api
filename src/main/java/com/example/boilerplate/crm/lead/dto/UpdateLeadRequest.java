package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.contact.dto.ContactInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Every editable field (an omitted optional one is cleared) and the version last read. */
public record UpdateLeadRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 200) String companyName,
    @Size(max = 254) @Pattern(regexp = ContactInput.EMAIL) String email,
    @Size(max = 50) String phone,
    @Size(max = 100) String jobTitle,
    Long sourceId,
    Long ownerId,
    Priority priority,
    LocalDate followUpDate,
    @Digits(integer = 13, fraction = 2)
        @PositiveOrZero
        @Schema(type = "string", example = "1250.00")
        BigDecimal estimatedValue,
    @Size(max = 3) String currency,
    @Size(max = 5000) String notes,
    @NotNull Long version)
    implements LeadInput {}
