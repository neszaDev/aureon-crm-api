package com.example.boilerplate.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    // Same rules as registration (RegisterRequest).
    @NotBlank @Size(min = 6, max = 72) String newPassword) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
  }
}
