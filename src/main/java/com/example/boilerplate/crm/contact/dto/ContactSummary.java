package com.example.boilerplate.crm.contact.dto;

import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** A row of the contact list. */
public record ContactSummary(
    long id,
    @NotNull String firstName,
    String lastName,
    @NotNull String fullName,
    String email,
    String phone,
    String jobTitle,
    Ref company,
    MemberRef owner,
    @NotNull List<String> tags,
    boolean archived,
    @NotNull Instant updatedAt) {}
