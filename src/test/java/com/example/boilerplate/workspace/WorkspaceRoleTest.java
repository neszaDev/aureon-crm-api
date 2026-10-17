package com.example.boilerplate.workspace;

import static com.example.boilerplate.workspace.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The role matrix of docs/crm/permissions.md, row by row. */
class WorkspaceRoleTest {
  @Test
  void ownerAndAdminHaveEveryPermission() {
    for (WorkspaceRole role : new WorkspaceRole[] {WorkspaceRole.OWNER, WorkspaceRole.ADMIN}) {
      assertThat(role.permissions())
          .containsExactlyInAnyOrder(
              RECORDS_EDIT_ANY,
              RECORDS_ASSIGN,
              EXPORT,
              WORKSPACE_MANAGE,
              SETTINGS_MANAGE,
              MEMBERS_MANAGE,
              AUDIT_READ);
    }
  }

  @Test
  void managerEditsAssignsAndExportsOnly() {
    assertThat(WorkspaceRole.MANAGER.permissions())
        .containsExactlyInAnyOrder(RECORDS_EDIT_ANY, RECORDS_ASSIGN, EXPORT);
    assertThat(WorkspaceRole.MANAGER.has(EXPORT)).isTrue();
    assertThat(WorkspaceRole.MANAGER.has(MEMBERS_MANAGE)).isFalse();
    assertThat(WorkspaceRole.MANAGER.has(WORKSPACE_MANAGE)).isFalse();
  }

  @Test
  void salesRepHasNoPermission() {
    assertThat(WorkspaceRole.SALES_REP.permissions()).isEmpty();
    for (Permission p : Permission.values()) {
      assertThat(WorkspaceRole.SALES_REP.has(p)).isFalse();
    }
  }
}
