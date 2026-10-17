package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AuthTokensResponse(
    @NotNull String accessToken,
    @NotNull String tokenType,
    long expiresIn,
    @NotNull String refreshToken,
    long refreshExpiresIn) {}
