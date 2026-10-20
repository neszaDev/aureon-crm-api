package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.workspace.dto.MemberRef;

/**
 * A member's activities in the period by type, and the tasks assigned to them completed in it; no
 * {@code user}: members who left (and tasks completed without an assignee). {@code total}: the five
 * counts together, the report's order.
 */
public record UserActivity(
    MemberRef user,
    long calls,
    long emails,
    long meetings,
    long notes,
    long tasksCompleted,
    long total) {}
