package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.settings.PipelineStage;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

/**
 * One entry of a deal's stage history, written when the deal is created (no "from" stage) and on
 * every move to another stage. The stage names are kept as they were then; the database nulls an id
 * when its stage is deleted, the app never changes a row. Not a {@code WorkspaceOwnedEntity}: it
 * has no update time or version, only the time of the change. The tenant filter scopes it all the
 * same.
 */
@Entity
@Immutable
@Table(name = "deal_stage_changes")
public class DealStageChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @TenantId
  @Column(name = "workspace_id", nullable = false, updatable = false)
  private Long workspaceId;

  @Column(nullable = false)
  private Long dealId;

  private Long fromStageId;

  @Column(length = 60)
  private String fromStageName;

  private Long toStageId;

  @Column(nullable = false, length = 60)
  private String toStageName;

  /** A user id without a foreign key: the history outlives the membership. */
  private Long changedBy;

  @Column(nullable = false)
  private Instant changedAt;

  protected DealStageChange() {}

  /** {@code from} is null for the deal's first entry; both stages' names are taken now. */
  public DealStageChange(long dealId, PipelineStage from, PipelineStage to, long changedBy) {
    this.dealId = dealId;
    if (from != null) {
      this.fromStageId = from.getId();
      this.fromStageName = from.getName();
    }
    this.toStageId = to.getId();
    this.toStageName = to.getName();
    this.changedBy = changedBy;
    this.changedAt = Instant.now();
  }

  /** Null for the first entry, or once the stage is deleted. */
  public Long getFromStageId() {
    return fromStageId;
  }

  /** Null for the first entry only. */
  public String getFromStageName() {
    return fromStageName;
  }

  /** Null once the stage is deleted. */
  public Long getToStageId() {
    return toStageId;
  }

  public String getToStageName() {
    return toStageName;
  }

  public Long getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
