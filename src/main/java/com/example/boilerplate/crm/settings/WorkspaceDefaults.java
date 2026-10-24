package com.example.boilerplate.crm.settings;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default pipeline and lead sources of a new workspace (docs/crm/README.md#deal). Plain JDBC
 * with an explicit workspace id: while a workspace is being created there is no workspace context,
 * so Hibernate's tenant would be none. MANDATORY: they commit with the workspace.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class WorkspaceDefaults {
  private record Stage(String name, int position, int probability, StageKind kind) {}

  private static final List<Stage> STAGES =
      List.of(
          new Stage("Qualification", 1, 10, StageKind.OPEN),
          new Stage("Needs analysis", 2, 25, StageKind.OPEN),
          new Stage("Proposal", 3, 50, StageKind.OPEN),
          new Stage("Negotiation", 4, 75, StageKind.OPEN),
          new Stage("Won", 5, 100, StageKind.WON),
          new Stage("Lost", 6, 0, StageKind.LOST));
  private static final List<String> SOURCES =
      List.of("Website", "Referral", "Event", "Outbound", "Partner", "Other");

  private final JdbcTemplate jdbc;

  public WorkspaceDefaults(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void seed(long workspaceId) {
    for (Stage s : STAGES) {
      jdbc.update(
          """
          INSERT INTO pipeline_stages
            (workspace_id, name, position, probability, kind, created_at, updated_at, version)
          VALUES (?, ?, ?, ?, ?, now(), now(), 0)""",
          workspaceId,
          s.name(),
          s.position(),
          s.probability(),
          s.kind().name());
    }
    for (String name : SOURCES) {
      jdbc.update(
          """
          INSERT INTO lead_sources (workspace_id, name, created_at, updated_at, version)
          VALUES (?, ?, now(), now(), 0)""",
          workspaceId,
          name);
    }
  }
}
