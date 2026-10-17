package com.example.boilerplate.crm.company.dto;

import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/** A company: the {@link CompanySummary} fields and the rest (api.md {@code Company}). */
public record CompanyResponse(
    long id,
    String name,
    String industry,
    String website,
    String phone,
    MemberRef owner,
    long contactCount,
    long openDealCount,
    boolean archived,
    Instant updatedAt,
    String address,
    Integer employeeCount,
    String notes,
    Instant archivedAt,
    Instant createdAt,
    long version) {}
