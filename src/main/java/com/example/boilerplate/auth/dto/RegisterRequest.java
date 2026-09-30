package com.example.boilerplate.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    // TODO: Change password min to 8 after implementing password strength validation
    @NotBlank @Size(min = 6, max = 72) String password) {}
