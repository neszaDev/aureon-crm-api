package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record WorkspaceSummaryResponse(
    long id, @NotNull String name, @NotNull WorkspaceRole role) {}
