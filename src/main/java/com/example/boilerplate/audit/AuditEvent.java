package com.example.boilerplate.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One line of the append-only trail. Not a {@code WorkspaceOwnedEntity}: the workspace is an
 * explicit column, because workspace creation is audited before any workspace context exists.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long workspaceId;

  @Column(nullable = false)
  private Instant occurredAt;

  private Long actorId;

  @Column(length = 254)
  private String actorEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AuditEntityType entityType;

  @Column(nullable = false)
  private Long entityId;

  @Column(length = 200)
  private String entityLabel;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> changes;

  @Column(length = 64)
  private String requestId;

  protected AuditEvent() {}

  AuditEvent(
      Long workspaceId,
      Instant occurredAt,
      Long actorId,
      String actorEmail,
      AuditAction action,
      AuditEntityType entityType,
      Long entityId,
      String entityLabel,
      Map<String, Object> changes,
      String requestId) {
    this.workspaceId = workspaceId;
    this.occurredAt = occurredAt;
    this.actorId = actorId;
    this.actorEmail = actorEmail;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityLabel = entityLabel;
    this.changes = changes;
    this.requestId = requestId;
  }

  public Long getId() {
    return id;
  }

  public Long getWorkspaceId() {
    return workspaceId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Long getActorId() {
    return actorId;
  }

  public String getActorEmail() {
    return actorEmail;
  }

  public AuditAction getAction() {
    return action;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public String getEntityLabel() {
    return entityLabel;
  }

  public Map<String, Object> getChanges() {
    return changes;
  }

  public String getRequestId() {
    return requestId;
  }
}
