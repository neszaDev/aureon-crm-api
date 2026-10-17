package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import java.time.Instant;

public record InvitationPreviewResponse(
    long workspaceId,
    String workspaceName,
    String email,
    WorkspaceRole role,
    Instant expiresAt,
    InvitationStatus status) {}
