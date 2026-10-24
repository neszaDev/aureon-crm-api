package com.example.boilerplate.user.dto;

import jakarta.validation.constraints.Size;

/** A blank or missing {@code displayName} clears it. */
public record UpdateProfileRequest(@Size(max = 100) String displayName) {}
