package com.example.boilerplate.testresult.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TestResultSummaryResponse(long total, @NotNull List<StatusCount> byStatus) {}
