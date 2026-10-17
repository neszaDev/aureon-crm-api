package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.SortField;

/** The {@code sort} of the company list. */
public enum CompanySort implements SortField {
  NAME("name"),
  CREATED_AT("createdAt"),
  UPDATED_AT("updatedAt");

  private final String property;

  CompanySort(String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public boolean ignoreCase() {
    return this == NAME;
  }
}
