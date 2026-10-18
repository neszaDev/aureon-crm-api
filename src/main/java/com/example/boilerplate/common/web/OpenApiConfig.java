package com.example.boilerplate.common.web;

import com.example.boilerplate.common.exception.ApiError;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
  private static final List<String> PAGE_FIELDS =
      List.of("content", "totalElements", "totalPages", "number", "size", "first", "last", "empty");

  /**
   * springdoc only lists what the controllers return; the error body every endpoint may answer with
   * (and its {@code code} enum) is added so clients can generate its type. Read by the converters
   * springdoc uses (those of its OpenAPI version, the converter below included).
   */
  @Bean
  OpenApiCustomizer errorBodySchema(SpringDocConfigProperties springDoc) {
    return api ->
        ModelConverters.getInstance(springDoc.isOpenapi31())
            .readAll(new AnnotatedType(ApiError.class))
            .forEach(api.getComponents()::addSchemas);
  }

  /**
   * Spring's {@code Page} always has these fields, but springdoc lists them as optional. The
   * wrappers (every schema named {@code Page...} that has a {@code content}; {@code PageableObject}
   * has none) say what they always carry.
   */
  @Bean
  OpenApiCustomizer pageFieldsAreRequired() {
    return api ->
        api.getComponents()
            .getSchemas()
            .forEach(
                (name, schema) -> {
                  if (name.startsWith("Page")
                      && schema.getProperties() != null
                      && schema.getProperties().containsKey("content")) {
                    PAGE_FIELDS.forEach(schema::addRequiredItem);
                  }
                });
  }

  /**
   * Which fields the generated types may count on. The app's Jackson leaves out nulls only, so a
   * record component of a primitive type is always there: required. Any other component is required
   * when it is {@code @NotNull} (swagger reads that itself), which response records put on what is
   * never null; the rest stays optional.
   */
  @Bean
  ModelConverter primitivesAreRequired() {
    return (type, context, chain) -> {
      Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
      Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
      if (schema == null || !raw.isRecord()) return schema;
      // A model mostly comes back as a $ref to its definition: mark the definition.
      String ref = schema.get$ref();
      Schema<?> model =
          ref == null
              ? schema
              : context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
      if (model != null && model.getProperties() != null) {
        for (RecordComponent c : raw.getRecordComponents()) {
          if (c.getType().isPrimitive() && model.getProperties().containsKey(c.getName())) {
            model.addRequiredItem(c.getName());
          }
        }
      }
      return schema;
    };
  }
}
