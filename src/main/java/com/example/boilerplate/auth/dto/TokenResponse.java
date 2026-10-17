package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.NotNull;

public record TokenResponse(
    @NotNull String accessToken, @NotNull String tokenType, long expiresIn) {}
