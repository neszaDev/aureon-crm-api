package com.example.boilerplate.crm.company.dto;

/**
 * The editable fields of a company, as create and update requests carry them (api.md {@code
 * CompanyInput}). The service normalizes and applies them the same way for both.
 */
public interface CompanyInput {
  /**
   * An http(s) URL without spaces, in any case. Blanks around it are allowed here because the
   * service strips them, like those of every text field.
   */
  String WEBSITE = "(?i)^\\s*https?://\\S+\\s*$";

  String name();

  String industry();

  String website();

  String phone();

  String address();

  Integer employeeCount();

  /** Create: null means the caller. Update: the owner to keep, reassign to, or null to unassign. */
  Long ownerId();

  String notes();
}
