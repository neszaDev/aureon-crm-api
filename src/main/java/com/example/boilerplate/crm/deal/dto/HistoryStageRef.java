package com.example.boilerplate.crm.deal.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A stage as a history entry names it: the name it had at the change; the id is null once the stage
 * is deleted.
 */
public record HistoryStageRef(Long id, @NotNull String name) {}
