package com.example.boilerplate.file.dto;

import java.time.Instant;

public record FileResponse(
    Long id, String name, String contentType, long size, Instant createdAt) {}
