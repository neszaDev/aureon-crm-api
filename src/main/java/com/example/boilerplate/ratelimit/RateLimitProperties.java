package com.example.boilerplate.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.rate-limit.*}; validated at startup. */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    @Valid @NotNull Limit loginPerIp,
    @Valid @NotNull Limit loginFailuresPerEmail,
    @Valid @NotNull Limit registerPerIp,
    @Min(100) long maxTrackedKeys) {

  /** {@code capacity} requests per {@code period}, refilled gradually. */
  public record Limit(@Min(1) long capacity, @NotNull @DurationMin(seconds = 1) Duration period) {}
}
