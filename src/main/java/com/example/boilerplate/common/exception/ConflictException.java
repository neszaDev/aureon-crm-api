package com.example.boilerplate.common.exception;

/** A rule of the data refuses the request: 409 with {@code code}. */
public class ConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ConflictException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
