package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.text.Texts;
import java.util.Optional;

/**
 * The {@code q} of a list: a case-insensitive substring match. The pattern keeps its case, so the
 * specification lowers both sides in SQL and Java's idea of lower case can't disagree with
 * Postgres': {@code cb.like(cb.lower(path), cb.lower(cb.literal(pattern)), '\\')} (in JPQL: {@code
 * lower(x) like lower(:pattern) escape '\'}).
 */
public final class TextSearch {
  private static final int MAX_LENGTH = 200;

  private TextSearch() {}

  /**
   * Empty when {@code q} is blank; otherwise the trimmed text with {@code \}, {@code %} and {@code
   * _} escaped by a backslash, between {@code %}. A longer {@code q} than 200 characters, or one
   * with a null character (which Postgres can't compare), is a 400 on {@code q}.
   */
  public static Optional<String> pattern(String q) {
    String text = Texts.optional(q);
    if (text == null) return Optional.empty();
    if (text.length() > MAX_LENGTH) throw new FieldValidationException("q", "is too long");
    if (text.indexOf('\0') >= 0) {
      throw new FieldValidationException("q", "must not contain null characters");
    }
    String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return Optional.of("%" + escaped + "%");
  }
}
