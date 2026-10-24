package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AuthTokensResponse(
    @NotNull String accessToken,
    @NotNull String tokenType,
    long expiresIn,
    @NotNull String refreshToken,
    long refreshExpiresIn) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "AuthTokensResponse[accessToken=***, tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + ", refreshToken=***, refreshExpiresIn="
        + refreshExpiresIn
        + "]";
  }
}
