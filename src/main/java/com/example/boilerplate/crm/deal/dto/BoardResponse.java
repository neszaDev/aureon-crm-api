package com.example.boilerplate.crm.deal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The pipeline board: every stage in position order, those without deals too. */
public record BoardResponse(@NotNull List<BoardColumn> stages) {}
