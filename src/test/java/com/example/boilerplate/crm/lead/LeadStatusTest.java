package com.example.boilerplate.crm.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeadStatusTest {
  // Rows: from, columns: to, both in declaration order (NEW, CONTACTED, QUALIFIED, UNQUALIFIED,
  // CONVERTED).
  private static final boolean[][] CAN_MOVE = {
    {false, true, true, true, false},
    {true, false, true, true, false},
    {true, true, false, true, false},
    {true, true, true, false, false},
    {false, false, false, false, false},
  };

  @Test
  void transitionTable() {
    LeadStatus[] all = LeadStatus.values();
    assertThat(all).hasSize(5);
    for (int from = 0; from < all.length; from++) {
      for (int to = 0; to < all.length; to++) {
        assertThat(all[from].canMoveTo(all[to]))
            .as("%s -> %s", all[from], all[to])
            .isEqualTo(CAN_MOVE[from][to]);
      }
    }
  }

  @Test
  void onlyTheFirstThreeAreOpen() {
    assertThat(LeadStatus.values())
        .filteredOn(LeadStatus::isOpen)
        .containsExactly(LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.QUALIFIED);
  }
}
