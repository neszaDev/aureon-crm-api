package com.example.boilerplate.common.web;

import com.example.boilerplate.common.exception.FieldValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** The page a list endpoint's {@code page} and {@code size} parameters ask for. */
public final class Paging {
  private static final int MAX_SIZE = 100;

  private Paging() {}

  /**
   * The page, or a 400 on {@code page} or {@code size}: the page at least 0, the size 1 to 100, and
   * the offset (page × size) within an int, which the query takes (past it Spring Data throws: a
   * 500).
   */
  public static PageRequest request(int page, int size, Sort sort) {
    if (page < 0) throw new FieldValidationException("page", "must not be negative");
    if (size < 1 || size > MAX_SIZE) {
      throw new FieldValidationException("size", "must be between 1 and " + MAX_SIZE);
    }
    if ((long) page * size > Integer.MAX_VALUE) {
      throw new FieldValidationException("page", "is too large");
    }
    return PageRequest.of(page, size, sort);
  }
}
