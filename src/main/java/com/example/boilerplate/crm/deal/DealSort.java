package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/**
 * The {@code sort} of the deal list: dates newest first and amounts largest first, names and close
 * dates ascending, unless told. {@code EXPECTED_CLOSE_DATE} has no keys: the list orders it in its
 * specification, deals without a date last in both directions.
 */
public enum DealSort implements SortField {
  UPDATED_AT(Direction.DESC, Key.of("updatedAt")),
  CREATED_AT(Direction.DESC, Key.of("createdAt")),
  AMOUNT(Direction.DESC, Key.of("amount")),
  NAME(Direction.ASC, Key.ignoringCase("name")),
  EXPECTED_CLOSE_DATE(Direction.ASC);

  private final Direction defaultDirection;
  private final List<Key> keys;

  DealSort(Direction defaultDirection, Key... keys) {
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
