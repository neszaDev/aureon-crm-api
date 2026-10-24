package com.example.boilerplate.crm.contact;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/**
 * The {@code sort} of the contact list. Contacts without a last name ({@code NAME}) or without a
 * company ({@code COMPANY}) come last ascending and first descending (PostgreSQL's null order).
 * Without a direction the names ascend and the dates come newest first.
 */
public enum ContactSort implements SortField {
  NAME(Direction.ASC, Key.ignoringCase("lastName"), Key.ignoringCase("firstName")),
  COMPANY(Direction.ASC, Key.ignoringCase("company.name")),
  CREATED_AT(Direction.DESC, Key.of("createdAt")),
  UPDATED_AT(Direction.DESC, Key.of("updatedAt"));

  private final Direction defaultDirection;
  private final List<Key> keys;

  ContactSort(Direction defaultDirection, Key... keys) {
    this.defaultDirection = defaultDirection;
    this.keys = List.of(keys);
  }

  @Override
  public Direction defaultDirection() {
    return defaultDirection;
  }

  @Override
  public List<Key> keys() {
    return keys;
  }
}
