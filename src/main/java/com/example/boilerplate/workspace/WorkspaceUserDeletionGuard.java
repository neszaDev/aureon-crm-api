package com.example.boilerplate.workspace;

import com.example.boilerplate.common.exception.ConflictException;
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
    if (members.isSoleOwnerOfAWorkspace(event.userId())) {
      throw new ConflictException(
          "This account is the only owner of a workspace; add another owner first");
    }
  }
}
