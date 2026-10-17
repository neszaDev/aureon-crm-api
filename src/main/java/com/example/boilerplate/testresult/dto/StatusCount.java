package com.example.boilerplate.testresult.dto;

import com.example.boilerplate.testresult.TestStatus;
import jakarta.validation.constraints.NotNull;

public record StatusCount(@NotNull TestStatus status, long count) {}
