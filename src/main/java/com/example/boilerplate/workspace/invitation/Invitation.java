package com.example.boilerplate.workspace.invitation;

import com.example.boilerplate.common.entity.AuditableEntity;
import com.example.boilerplate.user.User;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.invitation.dto.InvitationStatus;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A link to join a workspace. Not workspace-owned: it is read through its token by people who are
 * not members yet, so every query names the workspace explicitly.
 */
@Entity
@Table(name = "workspace_invitations")
public class Invitation extends AuditableEntity {
  @Column(nullable = false, updatable = false)
  private Long workspaceId;

  @Column(nullable = false, updatable = false, length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private WorkspaceRole role;

  @Column(nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invited_by", updatable = false)
  private User invitedBy;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant acceptedAt;

  private Instant revokedAt;

  protected Invitation() {}

  public Invitation(
      long workspaceId,
      String email,
      WorkspaceRole role,
      String tokenHash,
      User invitedBy,
      Instant expiresAt) {
    this.workspaceId = workspaceId;
    this.email = email;
    this.role = role;
    this.tokenHash = tokenHash;
    this.invitedBy = invitedBy;
    this.expiresAt = expiresAt;
  }

  public InvitationStatus status(Instant now) {
    if (acceptedAt != null) return InvitationStatus.ACCEPTED;
    if (revokedAt != null) return InvitationStatus.REVOKED;
    return now.isBefore(expiresAt) ? InvitationStatus.PENDING : InvitationStatus.EXPIRED;
  }

  public void accept(Instant now) {
    acceptedAt = now;
  }

  public void revoke(Instant now) {
    revokedAt = now;
  }

  public Long getWorkspaceId() {
    return workspaceId;
  }

  public String getEmail() {
    return email;
  }

  public WorkspaceRole getRole() {
    return role;
  }

  public User getInvitedBy() {
    return invitedBy;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
