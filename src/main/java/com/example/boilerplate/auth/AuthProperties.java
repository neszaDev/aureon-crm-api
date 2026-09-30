package com.example.boilerplate.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.security.*}; validated at startup so bad config fails fast with a clear message. */
@Validated
@ConfigurationProperties("app.security")
public record AuthProperties(
    @Valid @NotNull Jwt jwt, @NotNull @DurationMin(seconds = 1) Duration refreshTokenTtl) {

  public record Jwt(
      // Length is checked in JwtService: a @Size violation would print the secret in the
      // startup failure report.
      @NotBlank String secret, @NotNull @DurationMin(seconds = 1) Duration accessTokenTtl) {}
}
