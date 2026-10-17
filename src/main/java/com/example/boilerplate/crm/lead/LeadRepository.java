package com.example.boilerplate.crm.lead;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}. */
public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
  /** Archived leads included. */
  boolean existsBySourceId(long sourceId);

  /** Locks the row until the transaction ends: conversions of one lead run one after the other. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from Lead l where l.id = :id")
  Optional<Lead> findForUpdate(long id);
}
