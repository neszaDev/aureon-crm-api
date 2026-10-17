package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRequest(@NotNull WorkspaceRole role) {}
