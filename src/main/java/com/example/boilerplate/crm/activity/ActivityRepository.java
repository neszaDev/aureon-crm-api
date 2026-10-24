package com.example.boilerplate.crm.activity;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface ActivityRepository
    extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {
  /**
   * Rows of (author id, null once they left; type, count) of the activities that occurred from
   * {@code start} (inclusive) to {@code end} (exclusive). Only {@code authorId}'s unless it is
   * null.
   */
  @Query(
      """
      select a.authorId, a.type, count(a) from Activity a
      where a.occurredAt >= :start and a.occurredAt < :end
        and (:authorId is null or a.authorId = :authorId)
      group by a.authorId, a.type""")
  List<Object[]> countByAuthorAndType(Instant start, Instant end, Long authorId);

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
