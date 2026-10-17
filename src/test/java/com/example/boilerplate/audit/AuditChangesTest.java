package com.example.boilerplate.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
