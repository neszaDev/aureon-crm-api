package com.example.boilerplate.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
