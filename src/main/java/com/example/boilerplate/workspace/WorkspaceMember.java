package com.example.boilerplate.workspace;

import com.example.boilerplate.common.entity.AuditableEntity;
import jakarta.persistence.*;

/** A user's role in a workspace. Not workspace-owned: memberships are how tenants are found. */
@Entity
@Table(name = "workspace_members")
public class WorkspaceMember extends AuditableEntity {
  @Column(nullable = false, updatable = false)
  private Long workspaceId;

  @Column(nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WorkspaceRole role;

  protected WorkspaceMember() {}

  public WorkspaceMember(long workspaceId, long userId, WorkspaceRole role) {
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.role = role;
  }
}
