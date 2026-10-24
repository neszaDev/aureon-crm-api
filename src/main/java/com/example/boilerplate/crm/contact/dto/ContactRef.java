package com.example.boilerplate.crm.contact.dto;

import jakarta.validation.constraints.NotNull;

/** A possible duplicate of a contact being entered. */
public record ContactRef(long id, @NotNull String fullName, String email, String phone) {}
