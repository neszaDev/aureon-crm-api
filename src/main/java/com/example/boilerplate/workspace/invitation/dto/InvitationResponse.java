package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** {@code invitedBy} is absent once the inviter's account is deleted. */
public record InvitationResponse(
    long id,
    @NotNull String email,
    @NotNull WorkspaceRole role,
    MemberRef invitedBy,
    @NotNull Instant createdAt,
    @NotNull Instant expiresAt) {}
