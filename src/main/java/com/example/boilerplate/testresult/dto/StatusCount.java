package com.example.boilerplate.testresult.dto;

import com.example.boilerplate.testresult.TestStatus;

public record StatusCount(TestStatus status, long count) {}
