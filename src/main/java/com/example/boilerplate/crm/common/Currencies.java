package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.validation.IsoCurrency;
import java.util.Locale;

/**
 * The currency of a money field. Request DTO fields must not carry {@code @IsoCurrency} (it is
 * case-sensitive and would reject {@code eur} before {@link #resolve} upper-cases it); bound them
 * with {@code @Size(max = 3)} and resolve in the service.
 */
public final class Currencies {
  private Currencies() {}

  /** The requested code upper-cased, or the workspace default when none is requested (null). */
  public static String resolve(String requested, String workspaceDefault) {
    if (requested == null) return workspaceDefault;
    String code = requested.toUpperCase(Locale.ROOT);
    if (!IsoCurrency.Validator.isKnown(code)) {
      throw new FieldValidationException("currency", "is not a valid ISO 4217 code");
    }
    return code;
  }
}
