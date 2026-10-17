package com.example.boilerplate.workspace;

import com.example.boilerplate.common.entity.AuditableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A tenant. Not workspace-owned itself: it defines the workspaces. */
@Entity
@Table(name = "workspaces")
public class Workspace extends AuditableEntity {
  @Column(nullable = false, length = 100)
  private String name;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String defaultCurrency;

  @Column(nullable = false, length = 64)
  private String timezone;

  protected Workspace() {}

  public Workspace(String name, String defaultCurrency, String timezone) {
    apply(name, defaultCurrency, timezone);
  }

  public void update(String name, String defaultCurrency, String timezone) {
    apply(name, defaultCurrency, timezone);
  }

  // Private so the constructor does not call an overridable method (javac this-escape).
  private void apply(String name, String defaultCurrency, String timezone) {
    this.name = name;
    this.defaultCurrency = defaultCurrency;
    this.timezone = timezone;
  }

  public String getName() {
    return name;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public String getTimezone() {
    return timezone;
  }
}
