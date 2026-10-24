package com.example.boilerplate.crm.export;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.export.*}; validated at startup. {@code maxRows}: the most rows one export may have
 * (more is a 400), at most 30000: the lookups of the rows' links bind one parameter per id, and
 * PgJDBC allows 32767.
 */
@Validated
@ConfigurationProperties("app.export")
public record ExportProperties(@DefaultValue("10000") @Min(1) @Max(30000) int maxRows) {}
