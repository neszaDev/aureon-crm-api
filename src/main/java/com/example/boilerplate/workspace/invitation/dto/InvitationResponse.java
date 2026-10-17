package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/** {@code invitedBy} is absent once the inviter's account is deleted. */
public record InvitationResponse(
    long id,
    String email,
    WorkspaceRole role,
    MemberRef invitedBy,
    Instant createdAt,
    Instant expiresAt) {}
