package com.example.boilerplate.crm.export;

import java.util.Locale;

/**
 * A record's reference as people read it (api.md, Conventions): a letter for the type ({@code C}
 * company, {@code P} contact, {@code L} lead, {@code D} deal, {@code T} task), a dash, and the id
 * padded to 4 digits: {@code C-0042}, {@code D-12345}.
 */
public final class RecordRefs {
  private RecordRefs() {}

  public static String format(char prefix, long id) {
    // ROOT: a locale with other digits must not change the reference.
    return String.format(Locale.ROOT, "%c-%04d", prefix, id);
  }
}
