package com.example.boilerplate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.boilerplate.common.exception.RateLimitExceededException;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
  /** Time only moves when a test says so. */
  static final class ManualClock implements TimeMeter {
    long nanos;

    @Override
    public long currentTimeNanos() {
      return nanos;
    }

    @Override
    public boolean isWallClockBased() {
      return false;
    }

    void advance(Duration d) {
      nanos += d.toNanos();
    }
  }

  final ManualClock clock = new ManualClock();

  @Test
  void consumeThrowsOnceTheBucketIsEmptyWithRetryAfter() {
    RateLimiter limiter = limiter(true);
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");

    assertThatThrownBy(() -> limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1"))
        .isInstanceOfSatisfying(
            RateLimitExceededException.class,
            e -> assertThat(e.retryAfterSeconds()).isEqualTo(30)); // 2 per minute: one every 30 s
  }

  @Test
  void keysAndPoliciesHaveSeparateBuckets() {
    RateLimiter limiter = limiter(true);
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");

    assertThatCode(() -> limiter.consume(Policy.LOGIN_PER_IP, "2.2.2.2"))
        .doesNotThrowAnyException();
    assertThatCode(() -> limiter.consume(Policy.REGISTER_PER_IP, "1.1.1.1"))
        .doesNotThrowAnyException();
  }

  @Test
  void bucketsRefillOverTime() {
    RateLimiter limiter = limiter(true);
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");
    limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");

    clock.advance(Duration.ofSeconds(30));

    assertThatCode(() -> limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void checkDoesNotTakeAndRecordNeverThrows() {
    RateLimiter limiter = limiter(true);
    limiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");

    assertThatThrownBy(() -> limiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void disabledLimiterAllowsEverything() {
    RateLimiter limiter = limiter(false);
    for (int i = 0; i < 10; i++) limiter.consume(Policy.LOGIN_PER_IP, "1.1.1.1");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    limiter.record(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com");
    assertThatCode(() -> limiter.check(Policy.LOGIN_FAILURES_PER_EMAIL, "a@example.com"))
        .doesNotThrowAnyException();
  }

  private RateLimiter limiter(boolean enabled) {
    var twoPerMinute = new RateLimitProperties.Limit(2, Duration.ofMinutes(1));
    return new RateLimiter(
        new RateLimitProperties(enabled, twoPerMinute, twoPerMinute, twoPerMinute, 1000), clock);
  }
}
