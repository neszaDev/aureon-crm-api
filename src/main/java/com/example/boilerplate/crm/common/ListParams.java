package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.web.Paging;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/** Paging and sorting of a list endpoint, for {@code JpaSpecificationExecutor.findAll}. */
public final class ListParams {
  private ListParams() {}

  /**
   * Sorted by {@code property}, then by id in the same direction, so pages never overlap. Null
   * handling is the database's: Spring Data's Criteria sorting can't change it (see {@link
   * Ordering#nullsLast} for columns that must list nulls last).
   */
  public static Pageable pageable(int page, int size, Direction direction, String property) {
    return Paging.request(page, size, byIdToo(Sort.by(direction, property), direction));
  }

  /** As above, by each of the field's {@link SortField#keys keys} in turn, then by id. */
  public static Pageable pageable(int page, int size, Direction direction, SortField field) {
    return Paging.request(page, size, sort(field, direction));
  }

  /** The order of {@link #pageable(int, int, Direction, SortField)} without a page (an export). */
  public static Sort sort(SortField field, Direction direction) {
    List<Sort.Order> orders = new ArrayList<>();
    for (SortField.Key key : field.keys()) {
      Sort.Order order = new Sort.Order(direction, key.property());
      orders.add(key.ignoreCase() ? order.ignoreCase() : order);
    }
    return byIdToo(Sort.by(orders), direction);
  }

  private static Sort byIdToo(Sort sort, Direction direction) {
    return sort.getOrderFor("id") == null ? sort.and(Sort.by(direction, "id")) : sort;
  }

  /** The requested direction ({@code direction}), or the sort's own when the request has none. */
  public static Direction direction(Direction requested, SortField sort) {
    return requested == null ? sort.defaultDirection() : requested;
  }

  /** Only the page: the order comes from the specification ({@link Ordering#nullsLast}). */
  public static Pageable unsorted(int page, int size) {
    return Paging.request(page, size, Sort.unsorted());
  }
}
