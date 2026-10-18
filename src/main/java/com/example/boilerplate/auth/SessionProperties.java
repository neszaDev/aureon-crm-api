package com.example.boilerplate.auth;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.auth.*}: how long a session (a refresh-token family) may live and replay. */
@Validated
@ConfigurationProperties("app.auth")
public record SessionProperties(
    // A used refresh token presented again within this is a parallel refresh (401 only); later,
    // a replay that revokes its family.
    @NotNull @DurationMin(seconds = 0) Duration refreshReuseGrace,
    // From the sign-in; past it the family can't rotate and the user signs in again.
    @NotNull @DurationMin(seconds = 1) Duration sessionMaxAge) {}
