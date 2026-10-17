package com.example.boilerplate.workspace;

import static com.example.boilerplate.workspace.WorkspaceRole.OWNER;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.ForbiddenException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.dto.MemberResponse;
import com.example.boilerplate.workspace.dto.UpdateMemberRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current workspace's members. Only owners manage owners, and the last owner can't be demoted
 * or removed, nor leave (docs/crm/permissions.md).
 */
@Service
@Transactional
public class MemberService {
  private final WorkspaceMemberRepository members;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final AuditTrail audit;

  public MemberService(
      WorkspaceMemberRepository members,
      UserRepository users,
      WorkspaceAccess access,
      AuditTrail audit) {
    this.members = members;
    this.users = users;
    this.access = access;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> list() {
    return members.list(WorkspaceContext.workspaceId());
  }

  public MemberResponse update(long userId, UpdateMemberRequest r) {
    Membership caller = access.require(Permission.MEMBERS_MANAGE);
    WorkspaceMember target = member(caller, userId);
    WorkspaceRole from = target.getRole();
    if (from == OWNER || r.role() == OWNER) requireOwner(caller);
    if (from == OWNER && r.role() != OWNER) requireAnotherOwner(target);
    User user = user(userId);
    if (from != r.role()) {
      target.changeRole(r.role());
      audit.record(
          AuditAction.ROLE_CHANGE,
          AuditEntityType.MEMBER,
          userId,
          user.getEmail(),
          Map.of("role", Map.of("from", from.name(), "to", r.role().name())));
    }
    return new MemberResponse(
        userId, user.getEmail(), user.getDisplayName(), target.getRole(), target.getCreatedAt());
  }

  /** Removing yourself is leaving, which any member may do; anyone else needs MEMBERS_MANAGE. */
  public void remove(long userId) {
    Membership caller = WorkspaceContext.require();
    boolean leaving = userId == caller.userId();
    if (!leaving) access.require(Permission.MEMBERS_MANAGE);
    WorkspaceMember target = member(caller, userId);
    if (target.getRole() == OWNER) {
      requireOwner(caller);
      requireAnotherOwner(target);
    }
    String email = user(userId).getEmail();
    members.delete(target);
    audit.record(
        leaving ? AuditAction.LEAVE : AuditAction.MEMBER_REMOVE,
        AuditEntityType.MEMBER,
        userId,
        email,
        Map.of());
  }

  private WorkspaceMember member(Membership caller, long userId) {
    return members
        .findByWorkspaceIdAndUserId(caller.workspaceId(), userId)
        .orElseThrow(() -> new NotFoundException("Member not found"));
  }

  private User user(long userId) {
    return users.findById(userId).orElseThrow(() -> new NotFoundException("Member not found"));
  }

  private static void requireOwner(Membership caller) {
    if (caller.role() != OWNER)
      throw new ForbiddenException(ErrorCode.OWNER_ONLY, "Only owners can manage owners");
  }

  private void requireAnotherOwner(WorkspaceMember owner) {
    boolean another =
        members.lockOwners(owner.getWorkspaceId()).stream()
            .anyMatch(o -> !o.getUserId().equals(owner.getUserId()));
    if (!another) {
      throw new ConflictException(
          ErrorCode.LAST_OWNER, "A workspace needs an owner; make someone else an owner first");
    }
  }
}
