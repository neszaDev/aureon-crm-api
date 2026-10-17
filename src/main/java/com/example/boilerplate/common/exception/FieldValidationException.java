package com.example.boilerplate.common.exception;

/** A request field is wrong in a way only a service can tell; answered as a 400 with that field. */
public class FieldValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
