package com.example.boilerplate.workspace;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Permission and ownership checks for services, against the current request's membership. */
@Component
public class WorkspaceAccess {
  private final WorkspaceMemberRepository members;

  public WorkspaceAccess(WorkspaceMemberRepository members) {
    this.members = members;
  }

  public Membership require(Permission p) {
    Membership m = WorkspaceContext.require();
    if (!m.role().has(p)) throw forbidden();
    return m;
  }

  /** Any record with {@code RECORDS_EDIT_ANY}, otherwise only the caller's own. */
  public void requireEditable(Long ownerUserId) {
    Membership m = WorkspaceContext.require();
    if (!m.role().has(Permission.RECORDS_EDIT_ANY) && !Objects.equals(ownerUserId, m.userId())) {
      throw forbidden();
    }
  }

  /** The owner of a new or changed record: the caller by default; someone else needs assigning. */
  public long resolveOwner(Long requestedUserId) {
    Membership m = WorkspaceContext.require();
    if (requestedUserId == null || requestedUserId == m.userId()) return m.userId();
    if (!m.role().has(Permission.RECORDS_ASSIGN)) throw forbidden();
    if (!members.existsByWorkspaceIdAndUserId(m.workspaceId(), requestedUserId)) {
      throw new FieldValidationException("ownerId", "is not a member of this workspace");
    }
    return requestedUserId;
  }

  private static AccessDeniedException forbidden() {
    return new AccessDeniedException("Your role can't do this");
  }
}
