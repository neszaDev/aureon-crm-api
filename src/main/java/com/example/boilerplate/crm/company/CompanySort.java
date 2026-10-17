package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;

/** The {@code sort} of the company list. */
public enum CompanySort implements SortField {
  NAME(Key.ignoringCase("name")),
  CREATED_AT(Key.of("createdAt")),
  UPDATED_AT(Key.of("updatedAt"));

  private final List<Key> keys;

  CompanySort(Key... keys) {
    this.keys = List.of(keys);
  }

  @Override
  public List<Key> keys() {
    return keys;
  }
}
