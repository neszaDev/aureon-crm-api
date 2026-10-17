package com.example.boilerplate.common.exception;

/** A request field is wrong in a way only a service can tell; answered as a 400 with that field. */
public class FieldValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String field;
  private final ErrorCode code;

  public FieldValidationException(String field, String message) {
    this(field, message, ErrorCode.VALIDATION_FAILED);
  }

  public FieldValidationException(String field, String message, ErrorCode code) {
    super(message);
    this.field = field;
    this.code = code;
  }

  public String field() {
    return field;
  }

  public ErrorCode code() {
    return code;
  }
}
