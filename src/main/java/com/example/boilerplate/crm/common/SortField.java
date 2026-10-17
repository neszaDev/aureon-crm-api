package com.example.boilerplate.crm.common;

/**
 * A sort choice of a list endpoint; each record type has an enum of these (query: {@code sort}).
 */
public interface SortField {
  /** The entity attribute the list is ordered by. */
  String property();
}
