package com.example.boilerplate.testresult.dto;

import java.util.List;

public record TestResultSummaryResponse(long total, List<StatusCount> byStatus) {}
