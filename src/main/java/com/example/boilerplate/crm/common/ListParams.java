package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.util.ArrayList;
import java.util.List;
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
    return sorted(page, size, direction, List.of(new Sort.Order(direction, property)));
  }

  /** As above, by each of the field's {@link SortField#keys keys} in turn, then by id. */
  public static Pageable pageable(int page, int size, Direction direction, SortField field) {
    List<Sort.Order> orders = new ArrayList<>();
    for (SortField.Key key : field.keys()) {
      Sort.Order order = new Sort.Order(direction, key.property());
      orders.add(key.ignoreCase() ? order.ignoreCase() : order);
    }
    return sorted(page, size, direction, orders);
  }

  private static Pageable sorted(int page, int size, Direction direction, List<Sort.Order> orders) {
    check(page, size);
    Sort sort = Sort.by(orders);
    if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(direction, "id"));
    return PageRequest.of(page, size, sort);
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
