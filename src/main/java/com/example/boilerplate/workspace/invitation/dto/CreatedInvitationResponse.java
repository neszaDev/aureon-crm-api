package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/** The only response that carries the link's token: only its hash is stored. */
public record CreatedInvitationResponse(
    long id,
    String email,
    WorkspaceRole role,
    MemberRef invitedBy,
    Instant createdAt,
    Instant expiresAt,
    String token) {}
