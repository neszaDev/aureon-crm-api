package com.example.boilerplate.crm.common;

/**
 * A sort choice of a list endpoint; each record type has an enum of these (query: {@code sort}).
 */
public interface SortField {
  /** The entity attribute the list is ordered by. */
  String property();

  /**
   * Whether the text attribute sorts by its lower case. Names do, so "beta" lands between "Alpha"
   * and "Gamma" whatever the database collation (Alpine's PostgreSQL sorts upper case first).
   */
  default boolean ignoreCase() {
    return false;
  }
}
