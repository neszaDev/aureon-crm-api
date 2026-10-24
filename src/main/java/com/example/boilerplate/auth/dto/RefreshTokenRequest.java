package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank String refreshToken) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "RefreshTokenRequest[refreshToken=***]";
  }
}
