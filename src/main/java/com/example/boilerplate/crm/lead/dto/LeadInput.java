package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.common.Priority;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The editable fields of a lead, as create and update requests carry them (api.md {@code
 * LeadInput}). The service normalizes and applies them the same way for both.
 */
public interface LeadInput {
  String firstName();

  String lastName();

  /** A company that is only a name for now; conversion can create the company from it. */
  String companyName();

  /** Stored in lower case; the pattern is the contacts' ({@code ContactInput.EMAIL}). */
  String email();

  String phone();

  String jobTitle();

  Long sourceId();

  /** Create: null means the caller. Update: the owner to keep, reassign to, or null to unassign. */
  Long ownerId();

  /** Null means MEDIUM. */
  Priority priority();

  LocalDate followUpDate();

  /** With its {@link #currency}: a value alone takes the workspace's, a currency alone is a 400. */
  BigDecimal estimatedValue();

  /**
   * Upper-cased and checked by the service, which takes no {@code @IsoCurrency} (see Currencies).
   */
  String currency();

  String notes();
}
