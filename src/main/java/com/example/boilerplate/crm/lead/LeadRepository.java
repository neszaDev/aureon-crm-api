package com.example.boilerplate.crm.lead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Scoped to the current workspace by {@code @TenantId}. */
public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
  /** Archived leads included. */
  boolean existsBySourceId(long sourceId);
}
