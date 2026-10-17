package com.example.boilerplate.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@MappedSuperclass
public abstract class AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  // Microseconds, as stored: a response equals a later read on nanosecond clocks (Linux) too.
  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public Long getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }
}
