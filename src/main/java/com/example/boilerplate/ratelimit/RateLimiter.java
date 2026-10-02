package com.example.boilerplate.ratelimit;

import com.example.boilerplate.common.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets, one per policy and key (an IP or an email). Limits are per instance; see
 * docs/plans/0002-users-files-rate-limits.md for moving them to shared storage.
 */
@Component
public class RateLimiter {
  /** What is limited, and by which key. */
  public enum Policy {
    LOGIN_PER_IP,
    LOGIN_FAILURES_PER_EMAIL,
    REGISTER_PER_IP
  }

  private final RateLimitProperties properties;
  private final TimeMeter clock;
  private final Cache<String, Bucket> buckets;

  @Autowired
  public RateLimiter(RateLimitProperties properties) {
    this(properties, TimeMeter.SYSTEM_MILLISECONDS);
  }

  RateLimiter(RateLimitProperties properties, TimeMeter clock) {
    this.properties = properties;
    this.clock = clock;
    // A bucket untouched for its period is full again, so dropping it changes nothing. The size
    // bound keeps memory flat when an attacker sprays IPs or emails.
    Duration longest =
        Stream.of(
                properties.loginPerIp(),
                properties.loginFailuresPerEmail(),
                properties.registerPerIp())
            .map(RateLimitProperties.Limit::period)
            .max(Duration::compareTo)
            .orElseThrow();
    this.buckets =
        Caffeine.newBuilder()
            .maximumSize(properties.maxTrackedKeys())
            .expireAfterAccess(longest)
            .build();
  }

  /** Takes one request from the bucket, or throws when it is empty. */
  public void consume(Policy policy, String key) {
    if (!properties.enabled()) return;
    var probe = bucket(policy, key).tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) throw exceeded(probe.getNanosToWaitForRefill());
  }

  /** Throws when the bucket is empty, without taking from it (e.g. before checking a password). */
  public void check(Policy policy, String key) {
    if (!properties.enabled()) return;
    var probe = bucket(policy, key).estimateAbilityToConsume(1);
    if (!probe.canBeConsumed()) throw exceeded(probe.getNanosToWaitForRefill());
  }

  /** Takes one request if any is left; never throws (e.g. counting a failed login). */
  public void record(Policy policy, String key) {
    if (properties.enabled()) bucket(policy, key).tryConsume(1);
  }

  private Bucket bucket(Policy policy, String key) {
    RateLimitProperties.Limit limit =
        switch (policy) {
          case LOGIN_PER_IP -> properties.loginPerIp();
          case LOGIN_FAILURES_PER_EMAIL -> properties.loginFailuresPerEmail();
          case REGISTER_PER_IP -> properties.registerPerIp();
        };
    return buckets.get(
        policy + ":" + key,
        k ->
            Bucket.builder()
                .addLimit(
                    l ->
                        l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .withCustomTimePrecision(clock)
                .build());
  }

  private static RateLimitExceededException exceeded(long nanosToWait) {
    return new RateLimitExceededException(Duration.ofNanos(nanosToWait));
  }
}
