package com.example.boilerplate.crm.company.dto;

import com.example.boilerplate.audit.Derived;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A company: the {@link CompanySummary} fields and the rest (api.md {@code Company}). */
public record CompanyResponse(
    long id,
    @NotNull String name,
    String industry,
    String website,
    String phone,
    MemberRef owner,
    @Derived long contactCount,
    @Derived long openDealCount,
    @Derived boolean archived,
    @NotNull Instant updatedAt,
    String address,
    Integer employeeCount,
    String notes,
    Instant archivedAt,
    @NotNull Instant createdAt,
    long version) {}
