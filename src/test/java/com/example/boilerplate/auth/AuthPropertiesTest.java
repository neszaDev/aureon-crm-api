package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthPropertiesTest {
  private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @EnableConfigurationProperties(AuthProperties.class)
  static class Config {}

  @Test
  void bindsValidConfiguration() {
    runner
        .withPropertyValues(
            "app.security.jwt.secret=" + VALID_SECRET,
            "app.security.jwt.access-token-ttl=PT15M",
            "app.security.refresh-token-ttl=P30D")
        .run(
            ctx -> {
              var props = ctx.getBean(AuthProperties.class);
              assertThat(props.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
              assertThat(props.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
            });
  }

  @Test
  void rejectsBlankJwtSecret() {
    runner
        .withPropertyValues(
            "app.security.jwt.secret= ",
            "app.security.jwt.access-token-ttl=PT15M",
            "app.security.refresh-token-ttl=P30D")
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("app.security.jwt.secret"));
  }

  @Test
  void rejectsNonPositiveTtl() {
    runner
        .withPropertyValues(
            "app.security.jwt.secret=" + VALID_SECRET,
            "app.security.jwt.access-token-ttl=PT0S",
            "app.security.refresh-token-ttl=P30D")
        .run(
            ctx ->
                assertThat(ctx)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("app.security.jwt.accessTokenTtl"));
  }
}
