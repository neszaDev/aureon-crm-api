package com.example.boilerplate.crm.export;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An export's 200 in the API docs: CSV text, not springdoc's default wildcard-typed byte string.
 * The mapping has no {@code produces}, so a client accepting anything still gets the JSON error.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
    responseCode = "200",
    description = "The CSV file",
    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
public @interface CsvResponse {}
