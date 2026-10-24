package com.example.boilerplate.crm.company.dto;

import com.example.boilerplate.common.exception.FieldValidationException;

/**
 * The list's query parameters besides paging and sorting. {@code q}: name, website or phone contain
 * it (any case); {@code unassigned}: no owner (not with {@code ownerId}: 400); {@code industry}:
 * exactly, in any case; {@code archived}: only archived companies (default: only active ones).
 */
public record CompanyFilter(
    String q, Long ownerId, boolean unassigned, String industry, boolean archived) {
  public CompanyFilter {
    if (unassigned && ownerId != null) {
      throw new FieldValidationException("unassigned", "can't be combined with ownerId");
    }
  }
}
