package com.example.boilerplate.audit.dto;

/**
 * Who made a change, as the trail kept it: the id and email of the time. {@code displayName} is the
 * user's current one, and absent once the account is gone. Every component is optional: an event
 * may have no actor at all (then the response has none).
 */
public record AuditActor(Long userId, String email, String displayName) {}
