package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void amountsGetTwoFractionDigits() {
    assertThat(Money.scale2(new BigDecimal("1250"))).isEqualTo(new BigDecimal("1250.00"));
    assertThat(Money.scale2(new BigDecimal("1250.5"))).isEqualTo(new BigDecimal("1250.50"));
    assertThat(Money.scale2(new BigDecimal("1250.00"))).isEqualTo(new BigDecimal("1250.00"));
    assertThat(Money.scale2(new BigDecimal("1E+3"))).isEqualTo(new BigDecimal("1000.00"));
    assertThat(Money.scale2(BigDecimal.ZERO)).hasToString("0.00");
  }

  @Test
  void nullStaysNull() {
    assertThat(Money.scale2(null)).isNull();
  }

  @Test
  void aThirdFractionDigitIsNeverRoundedAway() {
    // Request validation (@Digits fraction = 2) keeps this out; here it must not round silently.
    assertThatThrownBy(() -> Money.scale2(new BigDecimal("1.005")))
        .isInstanceOf(ArithmeticException.class);
  }
}
