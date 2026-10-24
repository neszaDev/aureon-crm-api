package com.example.boilerplate.workspace.dto;

import com.example.boilerplate.common.validation.IanaTimeZone;
import com.example.boilerplate.common.validation.IsoCurrency;
import jakarta.validation.constraints.*;

public record CreateWorkspaceRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Pattern(regexp = "[A-Z]{3}") @IsoCurrency String defaultCurrency,
    @NotBlank @Size(max = 64) @IanaTimeZone String timezone) {}
