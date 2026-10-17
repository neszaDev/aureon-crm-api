package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import java.time.Instant;

public record MemberResponse(
    long userId, String email, String displayName, WorkspaceRole role, Instant joinedAt) {}
