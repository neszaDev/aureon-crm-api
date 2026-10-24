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
    requireEditableBy(ownerUserId);
  }

  /**
   * As {@link #requireEditable} for a record with several people attached (a task's assignee and
   * creator): without {@code RECORDS_EDIT_ANY} the caller must be one of {@code userIds}. Null
   * (someone who left) is nobody.
   */
  public void requireEditableBy(Long... userIds) {
    Membership m = WorkspaceContext.require();
    if (m.role().has(Permission.RECORDS_EDIT_ANY)) return;
    for (Long userId : userIds) {
      if (Objects.equals(userId, m.userId())) return;
    }
    throw forbidden();
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

  /**
   * A filter by person ({@code field} names it): null, or a current member of the workspace (else
   * 400 on {@code field}, so another workspace's or a removed member's id never reaches a query).
   */
  public Long member(String field, Long userId) {
    if (userId != null
        && !members.existsByWorkspaceIdAndUserId(WorkspaceContext.workspaceId(), userId)) {
      throw new FieldValidationException(field, "is not a member of this workspace");
    }
    return userId;
  }

  private long assign(Membership m, String field, long userId) {
    if (!m.role().has(Permission.RECORDS_ASSIGN)) throw forbidden();
    return member(field, userId);
  }

  private static AccessDeniedException forbidden() {
    return new AccessDeniedException("Your role can't do this");
  }
}
