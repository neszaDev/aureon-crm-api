package com.example.boilerplate.crm.company.dto;

import jakarta.validation.constraints.*;

public record CreateCompanyRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String industry,
    @Size(max = 255) @Pattern(regexp = CompanyInput.WEBSITE) String website,
    @Size(max = 50) String phone,
    @Size(max = 500) String address,
    @Min(0) @Max(10_000_000) Integer employeeCount,
    Long ownerId,
    @Size(max = 5000) String notes)
    implements CompanyInput {}
