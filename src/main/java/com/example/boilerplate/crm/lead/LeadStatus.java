package com.example.boilerplate.crm.lead;

import java.util.Arrays;
import java.util.List;

/**
 * Where a lead stands. The first four move freely among themselves; {@code CONVERTED} is reached
 * only through conversion and is final.
 */
public enum LeadStatus {
  NEW,
  CONTACTED,
  QUALIFIED,
  UNQUALIFIED,
  CONVERTED;

  private static final List<LeadStatus> OPEN =
      Arrays.stream(values()).filter(LeadStatus::isOpen).toList();

  /** The statuses of an open lead (NEW, CONTACTED, QUALIFIED), as {@link #isOpen} says. */
  public static List<LeadStatus> open() {
    return OPEN;
  }

  public boolean isOpen() {
    return this == NEW || this == CONTACTED || this == QUALIFIED;
  }

  /** Any two different statuses among the first four; nothing into or out of CONVERTED. */
  public boolean canMoveTo(LeadStatus target) {
    return this != target && this != CONVERTED && target != CONVERTED;
  }
}
