package com.example.boilerplate.user;

import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.admin.*}: the account {@link AdminBootstrap} creates at startup. Both blank (the
 * default) means no bootstrap admin.
 */
@Validated
@ConfigurationProperties("app.admin")
public record AdminProperties(
    @Email String email,
    // Length is checked in AdminBootstrap: a @Size violation would print the password in the
    // startup failure report.
    String password) {

  public boolean enabled() {
    return email != null && !email.isBlank();
  }
}
