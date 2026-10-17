package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.audit.Derived;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.workspace.dto.MemberRef;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A lead (api.md {@code Lead}), in the list and everywhere else. The converted references are null
 * until the lead is converted.
 */
public record LeadResponse(
    long id,
    @NotNull String firstName,
    String lastName,
    String companyName,
    String email,
    String phone,
    String jobTitle,
    Ref source,
    MemberRef owner,
    @NotNull Priority priority,
    LocalDate followUpDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", example = "1250.00")
        BigDecimal estimatedValue,
    String currency,
    String notes,
    @NotNull LeadStatus status,
    String unqualifiedReason,
    Instant convertedAt,
    Ref convertedCompany,
    PersonRef convertedContact,
    Ref convertedDeal,
    @Derived boolean archived,
    Instant archivedAt,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt,
    long version) {}
