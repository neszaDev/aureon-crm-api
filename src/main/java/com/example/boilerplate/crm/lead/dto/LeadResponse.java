package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.workspace.dto.MemberRef;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A lead (api.md {@code Lead}), in the list and everywhere else. The converted references are null
 * until the lead is converted.
 */
public record LeadResponse(
    long id,
    String firstName,
    String lastName,
    String companyName,
    String email,
    String phone,
    String jobTitle,
    Ref source,
    MemberRef owner,
    Priority priority,
    LocalDate followUpDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", example = "1250.00")
        BigDecimal estimatedValue,
    String currency,
    String notes,
    LeadStatus status,
    String unqualifiedReason,
    Instant convertedAt,
    Ref convertedCompany,
    Ref convertedContact,
    Ref convertedDeal,
    boolean archived,
    Instant archivedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
