package com.example.boilerplate.crm.deal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A move to another stage. {@code lostReason} is required for LOST and dropped for the others. */
public record MoveDealRequest(
    @NotNull Long stageId, @NotNull Long version, @Size(max = 500) String lostReason) {}
