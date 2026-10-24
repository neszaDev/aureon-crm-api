package com.example.boilerplate.crm.task;

import com.example.boilerplate.crm.common.SortField;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/**
 * The {@code sort} of the task list. {@code DUE_AT} and {@code PRIORITY} have no keys: the list
 * orders them in its specification, tasks without a due time last in both directions and HIGH
 * before MEDIUM before LOW descending.
 */
public enum TaskSort implements SortField {
  DUE_AT(Direction.ASC),
  PRIORITY(Direction.DESC),
  CREATED_AT(Direction.DESC, Key.of("createdAt")),
  UPDATED_AT(Direction.DESC, Key.of("updatedAt"));

  private final Direction defaultDirection;
  private final List<Key> keys;

  TaskSort(Direction defaultDirection, Key... keys) {
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
