package com.example.boilerplate.crm.contact.dto;

import com.example.boilerplate.common.exception.FieldValidationException;

/**
 * The list's query parameters besides paging and sorting. {@code q}: first or last name, both
 * together, email, phone or company name contain it (any case); {@code tag}: one of the contact's
 * tags, in any case; {@code unassigned}: no owner (not with {@code ownerId}: 400); {@code
 * archived}: only archived contacts (default: only active ones).
 */
public record ContactFilter(
    String q,
    Long companyId,
    Long ownerId,
    boolean unassigned,
    Long sourceId,
    String tag,
    boolean archived) {
  public ContactFilter {
    if (unassigned && ownerId != null) {
      throw new FieldValidationException("unassigned", "can't be combined with ownerId");
    }
  }
}
