package com.example.boilerplate.crm.contact.dto;

import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;
import java.util.List;

/** A contact: the {@link ContactSummary} fields and the rest (api.md {@code Contact}). */
public record ContactResponse(
    long id,
    String firstName,
    String lastName,
    String fullName,
    String email,
    String phone,
    String jobTitle,
    Ref company,
    MemberRef owner,
    List<String> tags,
    boolean archived,
    Instant updatedAt,
    Ref source,
    String notes,
    Instant archivedAt,
    Instant createdAt,
    long version) {}
