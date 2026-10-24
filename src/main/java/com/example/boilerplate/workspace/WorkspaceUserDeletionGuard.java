package com.example.boilerplate.workspace;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.user.UserDeletionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** A workspace always has an owner: its last owner's account can't be deleted (rolls back). */
@Component
class WorkspaceUserDeletionGuard {
  private final WorkspaceMemberRepository members;

  WorkspaceUserDeletionGuard(WorkspaceMemberRepository members) {
    this.members = members;
  }

  @EventListener
  void refuseLastOwner(UserDeletionEvent event) {
    // Locked, so an owner leaving at the same time can't leave the workspace with no owner.
    boolean soleOwner =
        members.lockOwnersOfWorkspacesOwnedBy(event.userId()).stream()
            .collect(groupingBy(WorkspaceMember::getWorkspaceId, counting()))
            .containsValue(1L);
    if (soleOwner) {
      throw new ConflictException(
          ErrorCode.ACCOUNT_OWNS_WORKSPACE,
          "This account is the only owner of a workspace; add another owner first");
    }
  }
}
