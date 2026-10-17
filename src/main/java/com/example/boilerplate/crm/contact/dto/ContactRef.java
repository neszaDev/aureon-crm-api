package com.example.boilerplate.crm.contact.dto;

/** A possible duplicate of a contact being entered. */
public record ContactRef(long id, String fullName, String email, String phone) {}
