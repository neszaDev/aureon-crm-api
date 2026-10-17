package com.example.boilerplate.crm.contact.dto;

import java.util.List;

/**
 * The editable fields of a contact, as create and update requests carry them (api.md {@code
 * ContactInput}). The service normalizes and applies them the same way for both.
 */
public interface ContactInput {
  /**
   * An address with a dotted domain and no spaces. Blanks around it are allowed here because the
   * service strips them, like those of every text field. Linear: no two parts can match the same
   * character.
   */
  String EMAIL = "(?U)^\\s*[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+\\s*$";

  String firstName();

  String lastName();

  /** Stored in lower case. */
  String email();

  String phone();

  String jobTitle();

  Long companyId();

  /** Create: null means the caller. Update: the owner to keep, reassign to, or null to unassign. */
  Long ownerId();

  Long sourceId();

  /** Checked and normalized by the service (stripped, lower case, sorted, at most 20). */
  List<String> tags();

  String notes();
}
