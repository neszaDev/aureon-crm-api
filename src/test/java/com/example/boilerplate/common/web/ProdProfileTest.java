package com.example.boilerplate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** What application-prod.yml resolves to, and that the app starts with it, without a server. */
class ProdProfileTest {

  @Test
  void theApiDocsAndSwaggerUiAreOffUnlessSpringdocEnabledIsTrue() throws Exception {
    StandardEnvironment off = prod(Map.of());
    assertThat(off.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    assertThat(off.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");

    StandardEnvironment on = prod(Map.of("SPRINGDOC_ENABLED", "true"));
    assertThat(on.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
    assertThat(on.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
  }

  @Test
  void prometheusStaysExposed() throws Exception {
    assertThat(prod(Map.of()).getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,prometheus");
  }

  /** With the docs off springdoc creates none of its beans; OpenApiConfig must not need them. */
  @Test
  void theAppStartsWithTheApiDocsOff() {
    new ApplicationContextRunner()
        .withUserConfiguration(OpenApiConfig.class)
        .withPropertyValues("springdoc.api-docs.enabled=false")
        .run(context -> assertThat(context).hasNotFailed());
  }

  private static StandardEnvironment prod(Map<String, Object> variables) throws Exception {
    var env = new StandardEnvironment();
    // In place of the machine's environment, so a real SPRINGDOC_ENABLED can't leak in.
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    env.getPropertySources().addFirst(new MapPropertySource("variables", variables));
    new YamlPropertySourceLoader()
        .load("prod", new ClassPathResource("application-prod.yml"))
        .forEach(env.getPropertySources()::addLast);
    return env;
  }
}
