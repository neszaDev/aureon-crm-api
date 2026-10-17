package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.crm.deal.DealStatus;
import java.time.LocalDate;

/**
 * The list's query parameters besides paging and sorting. {@code q}: the name or the company's name
 * contains it (any case); {@code status}: the stage's kind; {@code closeFrom}/{@code closeTo}: the
 * expected close date between them, both inclusive, so deals without one never match; {@code
 * unassigned}: no owner (not with {@code ownerId}: 400); {@code archived}: only archived deals
 * (default: only active ones).
 */
public record DealFilter(
    String q,
    Long stageId,
    DealStatus status,
    Long ownerId,
    boolean unassigned,
    Long companyId,
    Long contactId,
    LocalDate closeFrom,
    LocalDate closeTo,
    boolean archived) {
  public DealFilter {
    if (unassigned && ownerId != null) {
      throw new FieldValidationException("unassigned", "can't be combined with ownerId");
    }
  }
}
