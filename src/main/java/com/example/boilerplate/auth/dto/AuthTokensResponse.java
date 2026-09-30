package com.example.boilerplate.auth.dto;

public record AuthTokensResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn) {}
