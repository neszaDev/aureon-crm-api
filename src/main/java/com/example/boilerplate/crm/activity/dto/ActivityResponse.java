package com.example.boilerplate.crm.activity.dto;

import com.example.boilerplate.crm.activity.ActivityType;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/**
 * An activity (api.md {@code Activity}), in the list and everywhere else. {@code author} is missing
 * once that person left the workspace.
 */
public record ActivityResponse(
    long id,
    ActivityType type,
    String subject,
    String body,
    Instant occurredAt,
    MemberRef author,
    Ref company,
    PersonRef contact,
    PersonRef lead,
    Ref deal,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
