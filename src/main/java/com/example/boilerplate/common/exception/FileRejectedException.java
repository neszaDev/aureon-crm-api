package com.example.boilerplate.common.exception;

import org.springframework.http.HttpStatus;

/** An upload the API refuses: empty (400), too large (413) or of a type not allowed (415). */
public class FileRejectedException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final HttpStatus status;

  public FileRejectedException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
