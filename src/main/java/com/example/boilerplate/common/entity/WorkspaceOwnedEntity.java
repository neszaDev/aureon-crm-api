package com.example.boilerplate.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * A row of one workspace. Hibernate adds {@code workspace_id = <current workspace>} to every query
 * and sets it on insert, from {@code WorkspaceTenantResolver}.
 */
@MappedSuperclass
public abstract class WorkspaceOwnedEntity extends AuditableEntity {
  @TenantId
  @Column(name = "workspace_id", nullable = false, updatable = false)
  private Long workspaceId;

  public Long getWorkspaceId() {
    return workspaceId;
  }
}
