package com.example.boilerplate.crm.company.dto;

import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A row of the company list. */
public record CompanySummary(
    long id,
    @NotNull String name,
    String industry,
    String website,
    String phone,
    MemberRef owner,
    long contactCount,
    long openDealCount,
    boolean archived,
    @NotNull Instant updatedAt) {}
