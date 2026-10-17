package com.example.boilerplate.crm.deal;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

/**
 * One entry of a deal's stage history, written when the deal is created ({@code fromStageId} null)
 * and on every move to another stage; never changed. Not a {@code WorkspaceOwnedEntity}: it has no
 * update time or version, only the time of the change. The tenant filter scopes it all the same.
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

  @Column(nullable = false)
  private Long toStageId;

  /** A user id without a foreign key: the history outlives the membership. */
  private Long changedBy;

  @Column(nullable = false)
  private Instant changedAt;

  protected DealStageChange() {}

  DealStageChange(long dealId, Long fromStageId, long toStageId, long changedBy) {
    this.dealId = dealId;
    this.fromStageId = fromStageId;
    this.toStageId = toStageId;
    this.changedBy = changedBy;
    this.changedAt = Instant.now();
  }

  public Long getFromStageId() {
    return fromStageId;
  }

  public Long getToStageId() {
    return toStageId;
  }

  public Long getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
