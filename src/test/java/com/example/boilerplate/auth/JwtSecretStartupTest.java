package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

/**
 * The placeholder secrets are published in this repository, so anyone could sign tokens with them:
 * the app refuses to start with one unless the local, dev or test profile is active (and prod is
 * not).
 */
class JwtSecretStartupTest {
  static final List<String> PLACEHOLDERS =
      List.of(
          "development-only-change-this-secret-to-at-least-32-bytes",
          "replace-with-a-random-secret-at-least-32-bytes-long");

  @EnableConfigurationProperties(AuthProperties.class)
  @Import(JwtService.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Config.class)
          .withPropertyValues(
              "app.security.jwt.access-token-ttl=PT15M", "app.security.refresh-token-ttl=P30D");

  @Test
  void aPlaceholderSecretStopsTheStartOutsideLocalDevAndTest() {
    for (String secret : PLACEHOLDERS)
      for (String profiles : List.of("prod", "", "staging", "prod,dev"))
        runner
            .withPropertyValues(
                "app.security.jwt.secret=" + secret, "spring.profiles.active=" + profiles)
            .run(
                context ->
                    assertThat(context)
                        .as(profiles)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("JWT_SECRET")
                        .hasMessageNotContaining(secret));
  }

  @Test
  void aPlaceholderSecretIsFineUnderLocalDevAndTest() {
    for (String profiles : List.of("local", "dev", "test"))
      runner
          .withPropertyValues(
              "app.security.jwt.secret=" + PLACEHOLDERS.get(0),
              "spring.profiles.active=" + profiles)
          .run(context -> assertThat(context).as(profiles).hasNotFailed());
  }

  @Test
  void anyOtherSecretStartsUnderProd() {
    runner
        .withPropertyValues(
            "app.security.jwt.secret=" + "a".repeat(64), "spring.profiles.active=prod")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
