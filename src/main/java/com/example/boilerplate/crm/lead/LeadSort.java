package com.example.boilerplate.crm.lead;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/**
 * The {@code sort} of the lead list, each with the direction it takes when the request names none.
 * {@code NAME}: last name, then first name, ignoring case; leads without a last name come last
 * ascending and first descending (PostgreSQL's null order). {@code FOLLOW_UP_DATE} and {@code
 * PRIORITY} have no keys: the list orders them in its specification, leads without a follow-up date
 * last in both directions and HIGH before MEDIUM before LOW descending.
 */
public enum LeadSort implements SortField {
  CREATED_AT(Direction.DESC, Key.of("createdAt")),
  UPDATED_AT(Direction.DESC, Key.of("updatedAt")),
  FOLLOW_UP_DATE(Direction.ASC),
  PRIORITY(Direction.DESC),
  NAME(Direction.ASC, Key.ignoringCase("lastName"), Key.ignoringCase("firstName"));

  private final Direction defaultDirection;
  private final List<Key> keys;

  LeadSort(Direction defaultDirection, Key... keys) {
    this.defaultDirection = defaultDirection;
    this.keys = List.of(keys);
  }

  public Direction defaultDirection() {
    return defaultDirection;
  }

  @Override
  public List<Key> keys() {
    return keys;
  }
}
