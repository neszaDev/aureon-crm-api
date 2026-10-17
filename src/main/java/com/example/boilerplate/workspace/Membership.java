package com.example.boilerplate.workspace;

/** The caller's membership of the workspace of the current request. */
public record Membership(long workspaceId, long userId, String email, WorkspaceRole role) {}
