package com.example.boilerplate.common.exception;

import java.time.Duration;

/** Too many attempts; answered with 429 and {@code Retry-After}. */
public class RateLimitExceededException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final Duration retryAfter;

  public RateLimitExceededException(Duration retryAfter) {
    super("Too many attempts. Try again later.");
    this.retryAfter = retryAfter;
  }

  /** Whole seconds to wait, at least 1. */
  public long retryAfterSeconds() {
    return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
  }
}
