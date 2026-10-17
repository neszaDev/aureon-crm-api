package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record InvitationPreviewResponse(
    long workspaceId,
    @NotNull String workspaceName,
    @NotNull String email,
    @NotNull WorkspaceRole role,
    @NotNull Instant expiresAt,
    @NotNull InvitationStatus status) {}
