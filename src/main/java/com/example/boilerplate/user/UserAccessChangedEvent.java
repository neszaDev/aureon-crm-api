package com.example.boilerplate.user;

/**
 * Published inside the transaction when a user's email or role changes, so their existing sessions
 * (refresh tokens) can be revoked.
 */
public record UserAccessChangedEvent(Long userId) {}
