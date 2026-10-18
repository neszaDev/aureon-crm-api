package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.NotNull;

public record TokenResponse(
    @NotNull String accessToken, @NotNull String tokenType, long expiresIn) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "TokenResponse[accessToken=***, tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + "]";
  }
}
