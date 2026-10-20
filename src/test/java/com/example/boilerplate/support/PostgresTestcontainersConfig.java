package com.example.boilerplate.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL for integration tests; same major version as docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {
  /** The image every IT runs on, also MigrationsFromEmptyIT's own container. */
  public static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>(IMAGE);
  }
}
