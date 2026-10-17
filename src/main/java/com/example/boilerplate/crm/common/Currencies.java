package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Currencies {
  private static final Set<String> CODES =
      Currency.getAvailableCurrencies().stream()
          .map(Currency::getCurrencyCode)
          .collect(Collectors.toUnmodifiableSet());

  private Currencies() {}

  /** The requested code upper-cased, or the workspace default when none is requested (null). */
  public static String resolve(String requested, String workspaceDefault) {
    if (requested == null) return workspaceDefault;
    String code = requested.toUpperCase(Locale.ROOT);
    if (!CODES.contains(code)) {
      throw new FieldValidationException("currency", "is not a valid ISO 4217 code");
    }
    return code;
  }
}
