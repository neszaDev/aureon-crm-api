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

  /**
   * The owner (or assignee: {@code field} names it) of a new record: the caller when {@code
   * requested} is null. Anyone else needs {@code RECORDS_ASSIGN} (403) and must be a member (400).
   */
  public long ownerForCreate(String field, Long requested) {
    Membership m = WorkspaceContext.require();
    if (requested == null || requested == m.userId()) return m.userId();
    return assign(m, field, requested);
  }

  /**
   * The owner of a changed record. {@code requested} equal to {@code current} keeps it, with no
   * permission needed. Any change, to another member or to null (unassigned), needs {@code
   * RECORDS_ASSIGN} (403); a new owner must be a member (400).
   */
  public Long ownerForUpdate(String field, Long requested, Long current) {
    if (Objects.equals(requested, current)) return current;
    Membership m = WorkspaceContext.require();
    if (requested != null) return assign(m, field, requested);
    if (!m.role().has(Permission.RECORDS_ASSIGN)) throw forbidden();
    return null;
  }

  private long assign(Membership m, String field, long userId) {
    if (!m.role().has(Permission.RECORDS_ASSIGN)) throw forbidden();
    if (!members.existsByWorkspaceIdAndUserId(m.workspaceId(), userId)) {
      throw new FieldValidationException(field, "is not a member of this workspace");
    }
    return userId;
  }

  private static AccessDeniedException forbidden() {
    return new AccessDeniedException("Your role can't do this");
  }
}
