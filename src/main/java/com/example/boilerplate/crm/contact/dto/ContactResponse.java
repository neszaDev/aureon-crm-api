package com.example.boilerplate.crm.contact.dto;

import com.example.boilerplate.audit.Derived;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** A contact: the {@link ContactSummary} fields and the rest (api.md {@code Contact}). */
public record ContactResponse(
    long id,
    @NotNull String firstName,
    String lastName,
    @Derived @NotNull String fullName,
    String email,
    String phone,
    String jobTitle,
    Ref company,
    MemberRef owner,
    @NotNull List<String> tags,
    @Derived boolean archived,
    @NotNull Instant updatedAt,
    Ref source,
    String notes,
    Instant archivedAt,
    @NotNull Instant createdAt,
    long version) {}
