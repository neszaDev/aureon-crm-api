package com.example.boilerplate.common.exception;

/**
 * A rule refuses the request as a whole, not one of its fields: 400 with {@code code} and no {@code
 * fieldErrors}.
 */
public class BadRequestException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public BadRequestException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
