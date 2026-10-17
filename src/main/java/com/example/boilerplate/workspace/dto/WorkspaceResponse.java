package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceRole;
import java.time.Instant;
import java.util.List;

/**
 * {@code role} and {@code permissions} are the caller's, so the client can hide what it can't do.
 */
public record WorkspaceResponse(
    long id,
    String name,
    String defaultCurrency,
    String timezone,
    WorkspaceRole role,
    List<Permission> permissions,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
