package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/** Paging and sorting of a list endpoint, for {@code JpaSpecificationExecutor.findAll}. */
public final class ListParams {
  private static final int MAX_SIZE = 100;

  private ListParams() {}

  /**
   * Sorted by {@code property}, then by id in the same direction, so pages never overlap. Null
   * handling is the database's: Spring Data's Criteria sorting can't change it (see {@link
   * Ordering#nullsLast} for columns that must list nulls last).
   */
  public static Pageable pageable(int page, int size, Direction direction, String property) {
    check(page, size);
    Sort sort = Sort.by(direction, property);
    if (!property.equals("id")) sort = sort.and(Sort.by(direction, "id"));
    return PageRequest.of(page, size, sort);
  }

  public static Pageable pageable(int page, int size, Direction direction, SortField field) {
    return pageable(page, size, direction, field.property());
  }

  /** Only the page: the order comes from the specification ({@link Ordering#nullsLast}). */
  public static Pageable unsorted(int page, int size) {
    check(page, size);
    return PageRequest.of(page, size);
  }

  private static void check(int page, int size) {
    if (page < 0) throw new FieldValidationException("page", "must not be negative");
    if (size < 1 || size > MAX_SIZE) {
      throw new FieldValidationException("size", "must be between 1 and " + MAX_SIZE);
    }
  }
}
