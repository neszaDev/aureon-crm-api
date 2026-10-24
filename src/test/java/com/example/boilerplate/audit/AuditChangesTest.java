package com.example.boilerplate.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.common.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditChangesTest {
  @Test
  void diffListsChangedAndAddedKeysOnly() {
    Map<String, Map<String, Object>> diff =
        AuditChanges.diff(Map.of("a", 1, "b", 2), Map.of("a", 1, "b", 3, "c", 4));

    assertThat(diff.keySet()).containsExactlyInAnyOrder("b", "c");
    assertThat(diff.get("b")).containsEntry("from", 2).containsEntry("to", 3);
    // A key missing on one side is a null on that side, and the key stays in the entry.
    assertThat(diff.get("c")).containsEntry("from", null).containsEntry("to", 4);
  }

  @Test
  void equalMapsHaveNoDiff() {
    assertThat(AuditChanges.diff(Map.of("a", 1, "b", "x"), Map.of("b", "x", "a", 1))).isEmpty();
    assertThat(AuditChanges.diff(Map.of(), Map.of())).isEmpty();
  }

  @Test
  void removedKeysAndNullValuesAreNullSafe() {
    Map<String, Object> before = new HashMap<>();
    before.put("gone", "x");
    before.put("same", null);
    before.put("filled", null);
    Map<String, Object> after = new HashMap<>();
    after.put("same", null);
    after.put("filled", 5);

    Map<String, Map<String, Object>> diff = AuditChanges.diff(before, after);

    assertThat(diff.keySet()).containsExactlyInAnyOrder("gone", "filled");
    assertThat(diff.get("gone")).containsEntry("from", "x").containsEntry("to", null);
    assertThat(diff.get("filled")).containsEntry("from", null).containsEntry("to", 5);
  }

  @Test
  void snapshotTurnsARecordIntoItsFields() {
    record Row(String name, int size) {}

    assertThat(new AuditChanges(new ObjectMapper()).snapshot(new Row("Acme", 3)))
        .containsExactlyInAnyOrderEntriesOf(Map.of("name", "Acme", "size", 3));
  }

  record Row(String name, String note, long version, String createdAt, String updatedAt) {}

  @Test
  void recordDiffListsEditedFieldsWithoutBookkeeping() {
    Row before = new Row("Acme", "x", 1, "t0", "t1");
    Row after = new Row("Acme Ltd", null, 2, "t0-changed", "t2");

    assertThat(new AuditChanges(new ObjectMapper()).recordDiff(before, after))
        .containsOnlyKeys("name", "note")
        .containsEntry("name", Map.of("from", "Acme", "to", "Acme Ltd"));
  }

  @Test
  void recordDiffIsEmptyWhenOnlyBookkeepingChanged() {
    Row before = new Row("Acme", "x", 1, "t0", "t1");

    assertThat(
            new AuditChanges(new ObjectMapper())
                .recordDiff(before, new Row("Acme", "x", 2, "t0", "t2")))
        .isEmpty();
  }

  record Person(String firstName, String lastName, @Derived String fullName) {}

  @Test
  void recordDiffLeavesDerivedFieldsOut() {
    AuditChanges changes = new AuditChanges(new ObjectMapper());

    assertThat(
            changes.recordDiff(
                new Person("Ada", "Lovelace", "Ada Lovelace"), new Person("Ada", null, "Ada")))
        .containsOnlyKeys("lastName");
    // A record's last state too.
    assertThat(changes.recordDiff(new Person("Ada", "L", "Ada L"), Map.of()))
        .containsOnlyKeys("firstName", "lastName");
  }

  record Priced(String name, BigDecimal amount) {}

  @Test
  void amountsOfTheSameValueDoNotDiffOnceScaleNormalised() {
    AuditChanges audit = new AuditChanges(new ObjectMapper());
    // "1250" from a request, "1250.00" from numeric(15,2): the same money.
    Priced fromRequest = new Priced("Deal", Money.scale2(new BigDecimal("1250")));
    Priced fromDatabase = new Priced("Deal", Money.scale2(new BigDecimal("1250.00")));

    assertThat(audit.recordDiff(fromDatabase, fromRequest)).isEmpty();
    assertThat(
            audit.recordDiff(
                fromDatabase, new Priced("Deal", Money.scale2(new BigDecimal("1250.5")))))
        .containsOnlyKeys("amount");
  }
}
