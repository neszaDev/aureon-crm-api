package com.example.boilerplate.user.dto;

import com.example.boilerplate.user.Role;
import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {}
