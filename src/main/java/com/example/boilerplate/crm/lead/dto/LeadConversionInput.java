package com.example.boilerplate.crm.lead.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How to convert a lead (api.md {@code LeadConversionInput}). {@code companyId}: an active company
 * to link; otherwise {@code createCompany} creates one from the lead's company name (none without a
 * name). {@code contactId}: a contact to link; otherwise one is created from the lead. {@code
 * deal}: omitted for no deal.
 */
public record LeadConversionInput(
    Long companyId, @NotNull Boolean createCompany, Long contactId, @Valid DealDraft deal) {

  /**
   * The deal to create, with the lead's company, contact, source and owner. {@code stageId}: an
   * open stage, null for the first one; {@code currency}: null for the workspace's.
   */
  public record DealDraft(
      @NotBlank @Size(max = 200) String name,
      @NotNull @Digits(integer = 13, fraction = 2) @PositiveOrZero BigDecimal amount,
      @Size(max = 3) String currency,
      Long stageId,
      LocalDate expectedCloseDate) {}
}
