package com.example.boilerplate.common.text;

import com.example.boilerplate.common.exception.FieldValidationException;

/**
 * Free text from requests, without the spaces around it. {@code @NotBlank} trims only ASCII blanks
 * and {@code String.strip()} keeps no-break spaces, so a name of U+3000 or U+00A0 alone gets past
 * both; here every Unicode space counts as blank. {@code @Size} checks the raw value, so the
 * stripped one always fits.
 */
public final class Texts {
  private Texts() {}

  /** The stripped value; blank is a 400 on {@code field}. */
  public static String required(String field, String value) {
    String stripped = optional(value);
    if (stripped == null) throw new FieldValidationException(field, "must not be blank");
    return stripped;
  }

  /** The stripped value, or null when nothing but spaces is left. */
  public static String optional(String value) {
    if (value == null) return null;
    int start = 0;
    int end = value.length();
    while (start < end && isSpace(value.charAt(start))) start++;
    while (end > start && isSpace(value.charAt(end - 1))) end--;
    return start == end ? null : value.substring(start, end);
  }

  // U+0085 (next line) is neither, yet a Unicode space to regexes with (?U), as in email patterns.
  private static boolean isSpace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
  }
}
