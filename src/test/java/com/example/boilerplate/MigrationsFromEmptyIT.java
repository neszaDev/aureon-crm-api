package com.example.boilerplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.PostgresTestcontainersConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Every migration from an empty database, on a container of its own (the shared one was migrated by
 * the first context): Flyway, with the application's locations, applies each file under {@code
 * db/migration} once and validates, then the application starts on that database with Hibernate
 * validating the schema against the entities. A {@code @SpringBootTest}, not a plain {@code
 * SpringApplication}: on the test classpath that would scan {@code PostgresTestcontainersConfig}
 * and connect to its container instead.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MigrationsFromEmptyIT {
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(PostgresTestcontainersConfig.IMAGE);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /** Before the context loads (with the first test), so its own Flyway finds nothing to do. */
  @BeforeAll
  static void migrateTheEmptyDatabase() throws IOException {
    POSTGRES.start();
    Resource[] files =
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/**/*.sql");
    MigrationVersion highest =
        Arrays.stream(files)
            .map(Resource::getFilename)
            .filter(name -> name.startsWith("V"))
            .map(name -> MigrationVersion.fromVersion(name.substring(1, name.indexOf("__"))))
            .max(Comparator.naturalOrder())
            .orElseThrow();
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations(applicationFlywayLocations())
            .load();

    assertThat(flyway.info().applied()).isEmpty();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(files.length);
    assertThat(flyway.info().current().getVersion()).isEqualTo(highest);
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @AfterAll
  static void stopTheDatabase() {
    POSTGRES.stop();
  }

  /** {@code spring.flyway.locations} as application.yml sets them, or Spring Boot's default. */
  private static String[] applicationFlywayLocations() throws IOException {
    var env = new StandardEnvironment();
    new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml"))
        .forEach(env.getPropertySources()::addLast);
    return Binder.get(env)
        .bindOrCreate("spring.flyway", FlywayProperties.class)
        .getLocations()
        .toArray(String[]::new);
  }

  @Test
  void theApplicationStartsOnItWithHibernateValidatingTheSchema(
      @Autowired DataSource dataSource, @Autowired Environment env) throws SQLException {
    assertThat(dataSource.unwrap(HikariDataSource.class).getJdbcUrl())
        .isEqualTo(POSTGRES.getJdbcUrl());
    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
  }
}
