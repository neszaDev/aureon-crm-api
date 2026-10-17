package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * {@code role} and {@code permissions} are the caller's, so the client can hide what it can't do.
 */
public record WorkspaceResponse(
    long id,
    @NotNull String name,
    @NotNull String defaultCurrency,
    @NotNull String timezone,
    @NotNull WorkspaceRole role,
    @NotNull List<Permission> permissions,
    long version,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
