package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.task.dto.TaskResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** How many tasks match, and the first 20 of them. */
public record CountedTasks(long count, @NotNull List<TaskResponse> first) {}
