package com.example.boilerplate.crm.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
  private Money() {}

  /**
   * The amount with exactly two fraction digits ({@code 1250} becomes {@code 1250.00}), the scale
   * of {@code numeric(15,2)}. Entities assign money through this, so what a request sent and what
   * the database returns are the same value: no phantom audit change, and responses echo {@code
   * 1250.00}. Never rounds: requests are limited to two fraction digits ({@code @Digits}), so a
   * third one is a bug and throws.
   */
  public static BigDecimal scale2(BigDecimal amount) {
    return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY);
  }
}
