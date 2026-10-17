package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.text.Texts;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code q} of a list: a case-insensitive substring match. Use the pattern as {@code
 * lower(column) like :pattern escape '\'}.
 */
public final class TextSearch {
  private static final int MAX_LENGTH = 200;

  private TextSearch() {}

  /** Empty when {@code q} is null or blank; a {@code q} longer than 200 characters is a 400. */
  public static Optional<String> pattern(String q) {
    String text = Texts.optional(q);
    if (text == null) return Optional.empty();
    if (text.length() > MAX_LENGTH) throw new FieldValidationException("q", "is too long");
    String escaped =
        text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return Optional.of("%" + escaped + "%");
  }
}
