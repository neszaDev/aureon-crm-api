package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** The only response that carries the link's token: only its hash is stored. */
public record CreatedInvitationResponse(
    long id,
    @NotNull String email,
    @NotNull WorkspaceRole role,
    MemberRef invitedBy,
    @NotNull Instant createdAt,
    @NotNull Instant expiresAt,
    @NotNull String token) {}
