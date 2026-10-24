package com.example.boilerplate.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;
import java.util.Set;

/**
 * An IANA time zone id ({@code Europe/Paris}, {@code UTC}); offsets such as {@code +05:00} or
 * {@code UTC+7} are not. Null is left to {@code @NotBlank}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IanaTimeZone.Validator.class)
public @interface IanaTimeZone {
  String message() default "is not an IANA time zone";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<IanaTimeZone, String> {
    private static final Set<String> IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || IDS.contains(value);
    }
  }
}
