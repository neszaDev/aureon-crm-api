package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.lead.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A move to another status. {@code reason} is required for UNQUALIFIED (stored with the lead) and
 * ignored otherwise; CONVERTED is not a valid target, conversion has its own endpoint.
 */
public record LeadStatusRequest(
    @NotNull LeadStatus status, @Size(max = 500) String reason, @NotNull Long version) {}
