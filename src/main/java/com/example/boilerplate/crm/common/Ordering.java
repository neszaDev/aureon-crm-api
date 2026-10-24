package com.example.boilerplate.crm.common;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/**
 * Ordering for a column that must list "no value" last in both directions (a task's due date, a
 * deal's expected close date). Spring Data's Criteria sorting refuses null precedence ({@code
 * Sort.Order.nullsLast()} throws), so the order is part of the specification instead: combine it
 * with the filters and query with {@link ListParams#unsorted}, never with a sorted pageable. The
 * same goes for {@link #byPriority}, an order no column holds (HIGH, MEDIUM, LOW).
 */
public final class Ordering {
  private Ordering() {}

  /**
   * Orders by {@code property} ({@code direction}, nulls last), then by id in the same direction.
   * {@code property} is a plain attribute of the root. A count query is left unordered.
   */
  public static <T> Specification<T> nullsLast(String property, Direction direction) {
    return (root, query, cb) -> {
      if (isCount(query)) return null;
      Path<?> value = root.get(property);
      Path<?> id = root.get("id");
      Expression<Integer> missing = cb.<Integer>selectCase().when(cb.isNull(value), 1).otherwise(0);
      query.orderBy(
          cb.asc(missing),
          direction.isAscending() ? cb.asc(value) : cb.desc(value),
          direction.isAscending() ? cb.asc(id) : cb.desc(id));
      return null;
    };
  }

  /**
   * Orders by the {@link Priority} in {@code property}, HIGH before MEDIUM before LOW when {@code
   * direction} is descending (the reverse ascending), then by id in the same direction. {@code
   * property} is a plain attribute of the root. A count query is left unordered.
   */
  public static <T> Specification<T> byPriority(String property, Direction direction) {
    return (root, query, cb) -> {
      if (isCount(query)) return null;
      Path<?> value = root.get(property);
      Path<?> id = root.get("id");
      Expression<Integer> rank =
          cb.<Integer>selectCase()
              .when(cb.equal(value, Priority.HIGH), 3)
              .when(cb.equal(value, Priority.MEDIUM), 2)
              .otherwise(1);
      query.orderBy(
          direction.isAscending() ? cb.asc(rank) : cb.desc(rank),
          direction.isAscending() ? cb.asc(id) : cb.desc(id));
      return null;
    };
  }

  private static boolean isCount(CriteriaQuery<?> query) {
    Class<?> type = query == null ? Long.class : query.getResultType();
    return type == Long.class || type == long.class;
  }
}
