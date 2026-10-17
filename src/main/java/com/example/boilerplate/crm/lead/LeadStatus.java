package com.example.boilerplate.crm.lead;

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

  public boolean isOpen() {
    return this == NEW || this == CONTACTED || this == QUALIFIED;
  }

  /** Any two different statuses among the first four; nothing into or out of CONVERTED. */
  public boolean canMoveTo(LeadStatus target) {
    return this != target && this != CONVERTED && target != CONVERTED;
  }
}
