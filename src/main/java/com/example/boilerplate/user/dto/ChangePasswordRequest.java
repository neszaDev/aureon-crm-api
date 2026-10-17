package com.example.boilerplate.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    // Same rules as registration (RegisterRequest).
    @NotBlank @Size(min = 6, max = 72) String newPassword) {}
