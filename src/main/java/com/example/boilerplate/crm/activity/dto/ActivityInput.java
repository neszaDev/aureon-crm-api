package com.example.boilerplate.crm.activity.dto;

import com.example.boilerplate.crm.activity.ActivityType;
import java.time.Instant;

/**
 * The editable fields of an activity, as create and update requests carry them (api.md {@code
 * ActivityInput}). The service normalizes and applies them the same way for both.
 */
public interface ActivityInput {
  ActivityType type();

  String subject();

  String body();

  /** An instant; null means now. */
  Instant occurredAt();

  /** Null: taken from the contact, else the deal, when they have a company. */
  Long companyId();

  Long contactId();

  Long leadId();

  Long dealId();
}
