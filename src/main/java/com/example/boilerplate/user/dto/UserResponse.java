package com.example.boilerplate.user.dto;

import com.example.boilerplate.user.Role;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UserResponse(
    @NotNull Long id,
    @NotNull String email,
    String displayName,
    @NotNull Role role,
    @NotNull Instant createdAt) {}
