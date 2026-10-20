package com.example.boilerplate.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Primary;

/**
 * The application's {@link Clock} in the shared IT context: the system's, until a test pins it to
 * an instant (and unpins it after). The dashboard, the reports and the export read it.
 */
@Primary
public class TestClock extends Clock {
  private volatile Instant pinned;

  public void pin(Instant at) {
    pinned = at;
  }

  public void unpin() {
    pinned = null;
  }

  @Override
  public Instant instant() {
    Instant at = pinned;
    return at != null ? at : Instant.now();
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("UTC only");
  }
}
