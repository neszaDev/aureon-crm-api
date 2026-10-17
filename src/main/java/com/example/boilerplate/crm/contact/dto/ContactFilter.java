package com.example.boilerplate.crm.contact.dto;

/**
 * The list's query parameters besides paging and sorting. {@code q}: first or last name, both
 * together, email, phone or company name contain it (any case); {@code tag}: one of the contact's
 * tags, in any case; {@code archived}: only archived contacts (default: only active ones).
 */
public record ContactFilter(
    String q, Long companyId, Long ownerId, Long sourceId, String tag, boolean archived) {}
