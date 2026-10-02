package com.example.boilerplate.user;

/**
 * Published inside the transaction, just before a user is deleted, so features that keep data
 * outside the database (e.g. stored files) can clean it up. Database rows cascade on their own.
 */
public record UserDeletionEvent(Long userId) {}
