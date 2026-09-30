package com.example.boilerplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Context starts against real PostgreSQL: Flyway migrates and Hibernate validates the schema. */
class BoilerplateApplicationIT extends AbstractIntegrationTest {
  @Autowired Flyway flyway;

  @Test
  void allMigrationsAppliedAndNonePending() {
    var info = flyway.info();
    assertThat(info.pending()).isEmpty();
    assertThat(info.applied())
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
  }
}
