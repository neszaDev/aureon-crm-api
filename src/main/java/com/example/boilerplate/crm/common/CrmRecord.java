package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.entity.WorkspaceOwnedEntity;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * What companies, contacts, leads and deals share: an owner (a member's user id, null once they
 * left the workspace) and an archive time. Records are archived, never deleted.
 */
@MappedSuperclass
public abstract class CrmRecord extends WorkspaceOwnedEntity {
  private Long ownerId;

  private Instant archivedAt;

  public Long getOwnerId() {
    return ownerId;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  /**
   * Archiving twice keeps the first archive time, in microseconds as stored, so the answer equals a
   * later read.
   */
  public void archive() {
    if (archivedAt == null) archivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public void restore() {
    archivedAt = null;
  }

  /** The caller has already checked who may assign; null leaves the record unassigned. */
  public void changeOwner(Long ownerId) {
    this.ownerId = ownerId;
  }
}
