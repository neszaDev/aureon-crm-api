package com.example.boilerplate.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/** An ISO 4217 code known to the JDK ({@code EUR}); null is left to {@code @NotNull}. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoCurrency.Validator.class)
public @interface IsoCurrency {
  String message() default "is not an ISO 4217 currency";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<IsoCurrency, String> {
    private static final Set<String> CODES =
        Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    /** Case-sensitive: {@code EUR} is known, {@code eur} is not. */
    public static boolean isKnown(String code) {
      return CODES.contains(code);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || isKnown(value);
    }
  }
}
