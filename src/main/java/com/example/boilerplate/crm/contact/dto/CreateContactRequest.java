package com.example.boilerplate.crm.contact.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateContactRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 254) @Pattern(regexp = ContactInput.EMAIL) String email,
    @Size(max = 50) String phone,
    @Size(max = 100) String jobTitle,
    Long companyId,
    Long ownerId,
    Long sourceId,
    List<String> tags,
    @Size(max = 5000) String notes)
    implements ContactInput {}
