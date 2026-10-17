package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrmRecordTest {
  static class Row extends CrmRecord {}

  @Test
  void archiveKeepsTheFirstTimeAndRestoreClearsIt() throws Exception {
    Row row = new Row();
    assertThat(row.isArchived()).isFalse();

    row.archive();
    var first = row.getArchivedAt();
    assertThat(row.isArchived()).isTrue();
    Thread.sleep(2);
    row.archive();
    assertThat(row.getArchivedAt()).isEqualTo(first);

    row.restore();
    assertThat(row.isArchived()).isFalse();
    assertThat(row.getArchivedAt()).isNull();
  }

  @Test
  void ownerCanBeChangedAndCleared() {
    Row row = new Row();
    row.changeOwner(7L);
    assertThat(row.getOwnerId()).isEqualTo(7L);
    row.changeOwner(null);
    assertThat(row.getOwnerId()).isNull();
  }
}
