package com.example.boilerplate.user;

/**
 * Published inside the transaction, just before a user is deleted by the admin {@code actorId}, so
 * features that keep data outside the database (e.g. stored files) can clean it up, and pending
 * invitations the user sent are revoked. Database rows cascade on their own.
 */
public record UserDeletionEvent(Long userId, long actorId, String actorEmail) {}
