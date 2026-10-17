package com.example.boilerplate.common.exception;

/**
 * The {@code code} of an error body: what went wrong, stable, for clients that translate the
 * message. The JSON value is the name. The table with each code's status is in docs/crm/api.md.
 */
public enum ErrorCode {
  // 400
  VALIDATION_FAILED,
  TEXT_INVALID,
  PIPELINE_CHANGED,
  // 403
  FORBIDDEN,
  REGISTRATION_DISABLED,
  OWNER_ONLY,
  INVITATION_EMAIL_MISMATCH,
  // 404
  NOT_FOUND,
  // 409
  VERSION_CONFLICT,
  CONFLICT,
  EMAIL_TAKEN,
  OWN_ACCOUNT,
  ACCOUNT_OWNS_WORKSPACE,
  LAST_OWNER,
  ALREADY_MEMBER,
  ALREADY_INVITED,
  INVITATION_NOT_PENDING,
  DUPLICATE_NAME,
  STAGE_PROTECTED,
  STAGE_IN_USE,
  SOURCE_IN_USE,
  LEAD_CONVERTED,
  LEAD_NOT_QUALIFIED,
  TASK_STATUS_CONFLICT,
  // 429
  RATE_LIMITED
}
