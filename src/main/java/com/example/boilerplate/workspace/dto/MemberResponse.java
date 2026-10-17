package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MemberResponse(
    long userId,
    @NotNull String email,
    String displayName,
    @NotNull WorkspaceRole role,
    @NotNull Instant joinedAt) {}
