package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/** The {@code sort} of the company list: names ascending, dates newest first, unless told. */
public enum CompanySort implements SortField {
  NAME(Direction.ASC, Key.ignoringCase("name")),
  CREATED_AT(Direction.DESC, Key.of("createdAt")),
  UPDATED_AT(Direction.DESC, Key.of("updatedAt"));

  private final Direction defaultDirection;
  private final List<Key> keys;

  CompanySort(Direction defaultDirection, Key... keys) {
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
