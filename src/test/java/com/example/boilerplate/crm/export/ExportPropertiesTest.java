package com.example.boilerplate.crm.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExportPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @EnableConfigurationProperties(ExportProperties.class)
  static class Config {}

  @Test
  void defaultsToTenThousand() {
    runner.run(ctx -> assertThat(ctx.getBean(ExportProperties.class).maxRows()).isEqualTo(10000));
  }

  /** The row lookups bind one parameter per id, and PgJDBC allows 32,767. */
  @Test
  void takesOneToThirtyThousand() {
    for (String rows : new String[] {"1", "30000"}) {
      runner
          .withPropertyValues("app.export.max-rows=" + rows)
          .run(ctx -> assertThat(ctx).hasNotFailed());
    }
    for (String rows : new String[] {"0", "30001"}) {
      runner
          .withPropertyValues("app.export.max-rows=" + rows)
          .run(
              ctx ->
                  assertThat(ctx)
                      .getFailure()
                      .rootCause()
                      .hasMessageContaining("app.export.maxRows"));
    }
  }
}
