package com.example.boilerplate.workspace.dto;

import jakarta.validation.constraints.NotNull;

/** A person referenced by a record or an invitation (owner, assignee, author, inviter). */
public record MemberRef(long userId, @NotNull String email, String displayName) {}
