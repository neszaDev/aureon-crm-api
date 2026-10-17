package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.workspace.WorkspaceRole;

public record WorkspaceSummaryResponse(long id, String name, WorkspaceRole role) {}
