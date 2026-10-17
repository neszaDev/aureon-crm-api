package com.example.boilerplate.crm.company.dto;

import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/** A row of the company list. */
public record CompanySummary(
    long id,
    String name,
    String industry,
    String website,
    String phone,
    MemberRef owner,
    long contactCount,
    long openDealCount,
    boolean archived,
    Instant updatedAt) {}
