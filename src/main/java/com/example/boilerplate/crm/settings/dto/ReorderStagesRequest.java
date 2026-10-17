package com.example.boilerplate.crm.settings.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Every open stage id, once, in the new order. */
public record ReorderStagesRequest(@NotEmpty List<Long> stageIds) {}
