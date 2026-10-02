package com.example.boilerplate.user.dto;

import com.example.boilerplate.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Fields an admin may change; {@code null} leaves a field as it is. */
public record UpdateUserRequest(@Email @Size(max = 254) String email, Role role) {}
