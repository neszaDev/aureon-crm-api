package com.example.boilerplate.crm.insight;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reports' native SQL, for what JPQL can't say. Native SQL is not tenant-filtered: every query
 * binds the workspace itself and returns numbers, never entities (permissions.md).
 */
@Component
class ReportQueries {
  /**
   * Per deal of the cohort, the highest position among the open and Won stages its history entered
   * (Lost and deleted stages, whose rows have no stage id, count for nothing; null: none). The
   * history drives: every deal has one, and joining by ids is enough, since ids are unique across
   * workspaces and the composite foreign keys keep a history row's deal and stage in its workspace.
   */
  private static final String FUNNEL =
      """
      SELECT reached.top, count(*) AS deals
      FROM (
        SELECT max(s.position) AS top
        FROM deal_stage_changes c
        JOIN deals d ON d.id = c.deal_id
        LEFT JOIN pipeline_stages s ON s.id = c.to_stage_id AND s.kind <> 'LOST'
        WHERE c.workspace_id = :workspaceId AND d.archived_at IS NULL
          AND d.created_at >= :start AND d.created_at < :end
        GROUP BY c.deal_id
      ) AS reached
      GROUP BY reached.top
      """;

  private final NamedParameterJdbcTemplate jdbc;

  ReportQueries(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The active deals of {@code workspaceId} created from {@code start} (inclusive) to {@code end}
   * (exclusive), counted by the highest stage position they reached (null key: none).
   */
  Map<Integer, Long> funnel(long workspaceId, Instant start, Instant end) {
    Map<String, Object> params =
        Map.of(
            "workspaceId", workspaceId,
            "start", OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
            "end", OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
    Map<Integer, Long> deals = new HashMap<>();
    jdbc.query(
        FUNNEL,
        params,
        row -> {
          deals.put(row.getObject("top", Integer.class), row.getLong("deals"));
        });
    return deals;
  }
}
