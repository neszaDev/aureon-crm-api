package com.example.boilerplate.crm.contact;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;

/**
 * The {@code sort} of the contact list. Contacts without a last name ({@code NAME}) or without a
 * company ({@code COMPANY}) come last ascending and first descending (PostgreSQL's null order).
 */
public enum ContactSort implements SortField {
  NAME(Key.ignoringCase("lastName"), Key.ignoringCase("firstName")),
  COMPANY(Key.ignoringCase("company.name")),
  CREATED_AT(Key.of("createdAt")),
  UPDATED_AT(Key.of("updatedAt"));

  private final List<Key> keys;

  ContactSort(Key... keys) {
    this.keys = List.of(keys);
  }

  @Override
  public List<Key> keys() {
    return keys;
  }
}
