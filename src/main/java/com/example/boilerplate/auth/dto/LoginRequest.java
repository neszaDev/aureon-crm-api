package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=***]";
  }
}
