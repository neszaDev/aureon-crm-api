package com.example.boilerplate.crm.activity;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface ActivityRepository
    extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

  /**
   * A converted lead's activities join the contact, company and deal it became; a link they have
   * stays. A new version, so an edit based on an earlier read is a 409. Returns how many.
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Activity a set a.contactId = coalesce(a.contactId, :contactId),
        a.companyId = coalesce(a.companyId, :companyId), a.dealId = coalesce(a.dealId, :dealId),
        a.version = a.version + 1, a.updatedAt = :now
      where a.workspaceId = :workspaceId and a.leadId = :leadId""")
  int linkConvertedLead(
      long workspaceId, long leadId, long contactId, Long companyId, Long dealId, Instant now);
}
