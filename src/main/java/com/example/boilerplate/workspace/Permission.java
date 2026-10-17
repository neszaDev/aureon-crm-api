package com.example.boilerplate.workspace;

/** What a workspace role may do beyond reading and creating; see docs/crm/permissions.md. */
public enum Permission {
  RECORDS_EDIT_ANY,
  RECORDS_ASSIGN,
  EXPORT,
  WORKSPACE_MANAGE,
  SETTINGS_MANAGE,
  MEMBERS_MANAGE,
  AUDIT_READ
}
