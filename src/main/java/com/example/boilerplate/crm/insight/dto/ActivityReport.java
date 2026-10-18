package com.example.boilerplate.crm.insight.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What each member did in the period, most in total first. */
public record ActivityReport(@NotNull List<UserActivity> byUser) {}
