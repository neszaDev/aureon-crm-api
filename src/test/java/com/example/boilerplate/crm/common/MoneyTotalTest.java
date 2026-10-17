package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTotalTest {
  @Test
  void theAmountIsAJsonStringThatKeepsItsScale() throws Exception {
    assertThat(
            new ObjectMapper().writeValueAsString(new MoneyTotal("EUR", new BigDecimal("1250.00"))))
        .isEqualTo("{\"currency\":\"EUR\",\"amount\":\"1250.00\"}");
  }
}
