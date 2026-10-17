package com.example.boilerplate.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds the {@code changes} of an audit event: {@code {field: {"from": x, "to": y}}}. */
@Component
public class AuditChanges {
  private static final Set<String> BOOKKEEPING = Set.of("version", "createdAt", "updatedAt");

  private final ObjectMapper json;

  public AuditChanges(ObjectMapper json) {
    this.json = json;
  }

  /**
   * The record's fields as a map. The app's mapper leaves null fields out, which {@link #diff}
   * reads as null on that side.
   */
  public Map<String, Object> snapshot(Object record) {
    return json.convertValue(record, new TypeReference<>() {});
  }

  /**
   * What an edit changed in a response DTO: the diff of both snapshots without the bookkeeping that
   * every edit bumps and that says nothing about what was edited. Empty when nothing was edited.
   */
  public Map<String, Map<String, Object>> recordDiff(Object before, Object after) {
    Map<String, Map<String, Object>> changes = diff(snapshot(before), snapshot(after));
    changes.keySet().removeAll(BOOKKEEPING);
    return changes;
  }

  /** Changed keys only; a key missing on one side counts as null there. */
  public static Map<String, Map<String, Object>> diff(
      Map<String, Object> before, Map<String, Object> after) {
    Set<String> keys = new LinkedHashSet<>(before.keySet());
    keys.addAll(after.keySet());
    Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
    for (String key : keys) {
      Object from = before.get(key);
      Object to = after.get(key);
      if (Objects.equals(from, to)) continue;
      // Not Map.of: it refuses null, and from/to are null for an added or cleared field.
      Map<String, Object> change = new LinkedHashMap<>();
      change.put("from", from);
      change.put("to", to);
      changes.put(key, change);
    }
    return changes;
  }
}
