package com.example.boilerplate.workspace;

import static com.example.boilerplate.workspace.Permission.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum WorkspaceRole {
  OWNER(EnumSet.allOf(Permission.class)),
  ADMIN(EnumSet.allOf(Permission.class)),
  MANAGER(EnumSet.of(RECORDS_EDIT_ANY, RECORDS_ASSIGN, EXPORT)),
  SALES_REP(EnumSet.noneOf(Permission.class));

  private final Set<Permission> permissions;

  WorkspaceRole(Set<Permission> permissions) {
    this.permissions = Collections.unmodifiableSet(permissions);
  }

  /** In declaration order. */
  public Set<Permission> permissions() {
    return permissions;
  }

  public boolean has(Permission p) {
    return permissions.contains(p);
  }
}
