package com.example.boilerplate.crm.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/** Paging and sorting of a list endpoint. Controllers validate {@code page} and {@code size}. */
public final class ListParams {
  private ListParams() {}

  /**
   * Sorted by {@code property}, then by id in the same direction, so pages never overlap. Nulls
   * come last either way (no effect on a column that can't be null).
   */
  public static Pageable pageable(int page, int size, Direction direction, String property) {
    Sort sort = Sort.by(new Sort.Order(direction, property).nullsLast());
    if (!property.equals("id")) sort = sort.and(Sort.by(direction, "id"));
    return PageRequest.of(page, size, sort);
  }
}
