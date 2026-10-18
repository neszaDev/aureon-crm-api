package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.text.Texts;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.Optional;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

/**
 * The {@code q} of a list: a case-insensitive substring match. The pattern keeps its case, so the
 * specification lowers both sides in SQL and Java's idea of lower case can't disagree with
 * Postgres': {@code cb.like(cb.lower(path), cb.lower(bound(cb, pattern)), '\\')} (in JPQL: {@code
 * lower(x) like lower(:pattern) escape '\'}). Text from a request is always {@linkplain #bound
 * bound}, never a {@code cb.literal}.
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
    String text = Texts.param("q", q);
    if (text == null) return Optional.empty();
    if (text.length() > MAX_LENGTH) throw new FieldValidationException("q", "is too long");
    return Optional.of("%" + escape(text) + "%");
  }

  /**
   * {@code text} as a bound parameter of the query. Not {@code cb.literal(text)}: Hibernate writes
   * that into the SQL text, which a request must never reach (NoInlineLiteralTest).
   */
  public static Expression<String> bound(CriteriaBuilder cb, String text) {
    return ((HibernateCriteriaBuilder) cb).value(text);
  }

  /**
   * {@code text} for a {@code LIKE ... ESCAPE '\'} pattern: {@code \}, {@code %} and {@code _}
   * escaped.
   */
  public static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
