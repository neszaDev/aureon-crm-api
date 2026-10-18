package com.example.boilerplate.crm.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecordRefsTest {
  @Test
  void theIdIsPaddedToFourDigits() {
    assertThat(RecordRefs.format('D', 42)).isEqualTo("D-0042");
    assertThat(RecordRefs.format('C', 1)).isEqualTo("C-0001");
    assertThat(RecordRefs.format('P', 9999)).isEqualTo("P-9999");
  }

  @Test
  void aLongerIdKeepsAllItsDigits() {
    assertThat(RecordRefs.format('D', 12345)).isEqualTo("D-12345");
    assertThat(RecordRefs.format('T', Long.MAX_VALUE)).isEqualTo("T-" + Long.MAX_VALUE);
  }
}
