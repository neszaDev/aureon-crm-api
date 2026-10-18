package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.lead.dto.LeadResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** How many leads match, and the first 20 of them. */
public record CountedLeads(long count, @NotNull List<LeadResponse> first) {}
