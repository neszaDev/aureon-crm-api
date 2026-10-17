package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.boilerplate.common.exception.FieldValidationException;
import org.junit.jupiter.api.Test;

class CurrenciesTest {
  @Test
  void aRequestedCodeIsUpperCased() {
    assertThat(Currencies.resolve("eur", "USD")).isEqualTo("EUR");
    assertThat(Currencies.resolve("THB", "USD")).isEqualTo("THB");
  }

  @Test
  void noCodeMeansTheWorkspaceDefault() {
    assertThat(Currencies.resolve(null, "USD")).isEqualTo("USD");
  }

  @Test
  void anythingElseIsRejectedOnTheCurrencyField() {
    for (String bad : new String[] {"XYZ", "EU", "EURO", "", " eur", "e1r"}) {
      assertThatThrownBy(() -> Currencies.resolve(bad, "USD"))
          .as(bad)
          .isInstanceOfSatisfying(
              FieldValidationException.class,
              e -> {
                assertThat(e.field()).isEqualTo("currency");
                assertThat(e).hasMessage("is not a valid ISO 4217 code");
              });
    }
  }
}
