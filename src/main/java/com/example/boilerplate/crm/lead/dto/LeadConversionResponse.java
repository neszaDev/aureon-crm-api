package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import jakarta.validation.constraints.NotNull;

/**
 * What a conversion linked or created (api.md {@code LeadConversion}). {@code alreadyConverted}:
 * the lead was converted before, and this is that first result; nothing was written.
 */
public record LeadConversionResponse(
    long leadId, Ref company, @NotNull PersonRef contact, Ref deal, boolean alreadyConverted) {}
