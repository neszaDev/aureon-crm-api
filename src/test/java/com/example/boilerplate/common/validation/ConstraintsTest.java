package com.example.boilerplate.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConstraintsTest {
  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Input(@IsoCurrency String currency, @IanaTimeZone String zone) {}

  private static Set<String> invalid(String currency, String zone) {
    return validator.validate(new Input(currency, zone)).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void knownCodesAndIanaZonesAreValid() {
    assertThat(invalid("EUR", "Europe/Paris")).isEmpty();
    assertThat(invalid("THB", "UTC")).isEmpty();
    assertThat(invalid("USD", "America/New_York")).isEmpty();
    assertThat(invalid(null, null)).isEmpty();
  }

  @Test
  void unknownCodesAndNonIanaZonesAreInvalid() {
    assertThat(invalid("XYZ", "Mars/Base")).containsExactlyInAnyOrder("currency", "zone");
    assertThat(invalid("eur", "europe/paris")).containsExactlyInAnyOrder("currency", "zone");
    for (String offset : new String[] {"+05:00", "UTC+7", "Z", ""}) {
      assertThat(invalid("EUR", offset)).containsExactly("zone");
    }
  }
}
