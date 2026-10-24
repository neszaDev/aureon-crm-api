package com.example.boilerplate.crm.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The editable fields of a deal, as create and update requests carry them (api.md {@code
 * DealInput}); the stage is set on create and changed by a move only. The service normalizes and
 * applies them the same way for both.
 */
public interface DealInput {
  String name();

  BigDecimal amount();

  /**
   * Null means the workspace's (on a PUT too); upper-cased and checked by the service, which takes
   * no {@code @IsoCurrency} (see Currencies).
   */
  String currency();

  /** The deal's own probability; null means its stage's. */
  Integer probability();

  LocalDate expectedCloseDate();

  /** Create: null means the caller. Update: the owner to keep, reassign to, or null to unassign. */
  Long ownerId();

  Long companyId();

  Long contactId();

  Long sourceId();

  String notes();
}
