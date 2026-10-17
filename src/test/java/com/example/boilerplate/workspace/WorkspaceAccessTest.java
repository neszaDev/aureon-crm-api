package com.example.boilerplate.workspace;

import static com.example.boilerplate.workspace.Permission.*;
import static com.example.boilerplate.workspace.WorkspaceRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.boilerplate.common.exception.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkspaceAccessTest {
  private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
  private final WorkspaceAccess access = new WorkspaceAccess(members);

  private static Membership as(WorkspaceRole role) {
    return new Membership(10, 1, "me@example.com", role);
  }

  @Test
  void requireChecksThePermissionOfTheRole() {
    assertThat(WorkspaceContext.runAs(as(ADMIN), () -> access.require(WORKSPACE_MANAGE)).role())
        .isEqualTo(ADMIN);
    WorkspaceContext.runAs(
        as(MANAGER),
        () -> assertThrows(AccessDeniedException.class, () -> access.require(WORKSPACE_MANAGE)));
  }

  @Test
  void withoutEditAnyOnlyYourOwnRecordsAreEditable() {
    WorkspaceContext.runAs(
        as(SALES_REP),
        () -> {
          access.requireEditable(1L);
          assertThrows(AccessDeniedException.class, () -> access.requireEditable(2L));
          // An unowned record needs RECORDS_EDIT_ANY.
          return assertThrows(AccessDeniedException.class, () -> access.requireEditable(null));
        });
    WorkspaceContext.runAs(
        as(MANAGER),
        () -> {
          access.requireEditable(2L);
          access.requireEditable(null);
          return null;
        });
  }

  @Test
  void resolveOwnerDefaultsToTheCallerAndOthersNeedAssignAndMembership() {
    when(members.existsByWorkspaceIdAndUserId(10, 2)).thenReturn(true);
    WorkspaceContext.runAs(
        as(SALES_REP),
        () -> {
          assertThat(access.resolveOwner(null)).isEqualTo(1);
          assertThat(access.resolveOwner(1L)).isEqualTo(1);
          return assertThrows(AccessDeniedException.class, () -> access.resolveOwner(2L));
        });
    WorkspaceContext.runAs(
        as(MANAGER),
        () -> {
          assertThat(access.resolveOwner(2L)).isEqualTo(2);
          FieldValidationException e =
              assertThrows(FieldValidationException.class, () -> access.resolveOwner(3L));
          assertThat(e.field()).isEqualTo("ownerId");
          return e;
        });
  }

  @Test
  void runAsRestoresThePreviousContext() {
    Membership outer = as(OWNER);
    Membership inner = new Membership(20, 1, "me@example.com", SALES_REP);
    WorkspaceContext.runAs(
        outer,
        () -> {
          assertThat(WorkspaceContext.runAs(inner, WorkspaceContext::workspaceId)).isEqualTo(20);
          return assertThat(WorkspaceContext.require()).isEqualTo(outer);
        });
    assertThat(WorkspaceContext.current()).isEmpty();
  }

  @Test
  void withoutAContextChecksFailAndTheTenantMatchesNothing() {
    assertThrows(IllegalStateException.class, () -> access.require(EXPORT));
    WorkspaceTenantResolver tenants = new WorkspaceTenantResolver();
    assertThat(tenants.resolveCurrentTenantIdentifier()).isEqualTo(WorkspaceTenantResolver.NONE);
    assertThat(WorkspaceContext.runAs(as(OWNER), tenants::resolveCurrentTenantIdentifier))
        .isEqualTo(10L);
    assertThat(tenants.isRoot(WorkspaceTenantResolver.NONE)).isFalse();
  }
}
