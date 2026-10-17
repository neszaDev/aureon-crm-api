package com.example.boilerplate.common.web;

import com.example.boilerplate.common.exception.ApiError;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
  /**
   * springdoc only lists what the controllers return; the error body every endpoint may answer with
   * (and its {@code code} enum) is added so clients can generate its type.
   */
  @Bean
  OpenApiCustomizer errorBodySchema() {
    return api ->
        ModelConverters.getInstance()
            .readAll(new AnnotatedType(ApiError.class))
            .forEach(api.getComponents()::addSchemas);
  }
}
