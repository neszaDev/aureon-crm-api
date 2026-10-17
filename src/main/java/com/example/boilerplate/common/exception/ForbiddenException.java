package com.example.boilerplate.common.exception;

import org.springframework.security.access.AccessDeniedException;

/** A 403 with a code more specific than {@link ErrorCode#FORBIDDEN} (a missing permission). */
public class ForbiddenException extends AccessDeniedException {
  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ForbiddenException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
