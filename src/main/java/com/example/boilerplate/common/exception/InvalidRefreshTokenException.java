package com.example.boilerplate.common.exception;

public class InvalidRefreshTokenException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidRefreshTokenException() {
    super("Refresh token is invalid or expired");
  }
}
