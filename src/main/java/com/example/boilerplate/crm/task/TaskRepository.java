package com.example.boilerplate.crm.task;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface TaskRepository
    extends JpaRepository<CrmTask, Long>, JpaSpecificationExecutor<CrmTask> {
  long countByStatusAndDueAtBefore(TaskStatus status, Instant now);

  /**
   * Rows of (assignee id, null when unassigned; count) of the tasks completed from {@code start}
   * (inclusive) to {@code end} (exclusive): only a completed task has a completion time.
   */
  @Query(
      """
      select t.assigneeId, count(t) from CrmTask t
      where t.completedAt >= :start and t.completedAt < :end
      group by t.assigneeId""")
  List<Object[]> countCompletedByAssignee(Instant start, Instant end);

  /** As {@code ActivityRepository.linkConvertedLead}, for the lead's tasks. */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update CrmTask t set t.contactId = coalesce(t.contactId, :contactId),
        t.companyId = coalesce(t.companyId, :companyId), t.dealId = coalesce(t.dealId, :dealId),
        t.version = t.version + 1, t.updatedAt = :now
      where t.workspaceId = :workspaceId and t.leadId = :leadId""")
  int linkConvertedLead(
      long workspaceId, long leadId, long contactId, Long companyId, Long dealId, Instant now);
}
