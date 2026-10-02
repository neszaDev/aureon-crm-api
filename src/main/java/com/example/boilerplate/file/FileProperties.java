package com.example.boilerplate.file;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** {@code app.files.*}; validated at startup. */
@Validated
@ConfigurationProperties("app.files")
public record FileProperties(
    @NotNull Path storageDir, @NotNull DataSize maxSize, @NotEmpty List<String> allowedTypes) {}
