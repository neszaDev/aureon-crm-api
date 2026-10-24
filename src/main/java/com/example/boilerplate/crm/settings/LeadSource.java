package com.example.boilerplate.crm.settings;

import com.example.boilerplate.common.entity.WorkspaceOwnedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "lead_sources")
public class LeadSource extends WorkspaceOwnedEntity {
  @Column(nullable = false, length = 60)
  private String name;

  private Instant archivedAt;

  protected LeadSource() {}

  public LeadSource(String name) {
    this.name = name;
  }

  /** Archiving keeps the first archive time (in microseconds, as stored); restoring clears it. */
  public void update(String name, boolean archived) {
    this.name = name;
    if (!archived) archivedAt = null;
    else if (archivedAt == null) archivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public String getName() {
    return name;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }
}
