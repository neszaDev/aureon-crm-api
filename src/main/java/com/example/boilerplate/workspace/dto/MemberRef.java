package com.example.boilerplate.workspace.dto;

/** A person referenced by a record or an invitation (owner, assignee, author, inviter). */
public record MemberRef(long userId, String email, String displayName) {}
