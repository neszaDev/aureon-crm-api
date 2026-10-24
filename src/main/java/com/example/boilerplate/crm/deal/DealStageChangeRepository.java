package com.example.boilerplate.crm.deal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Scoped to the current workspace by {@code @TenantId}, except for the native {@link #signatures}.
 */
public interface DealStageChangeRepository extends JpaRepository<DealStageChange, Long> {
  List<DealStageChange> findByDealIdOrderByChangedAtDescIdDesc(long dealId);

  /**
   * When each deal entered its current stage, the time of its newest entry: {@code [dealId,
   * changedAt]} rows, one per deal with a history, in one query for a page of deals.
   */
  @Query(
      """
      select c.dealId, max(c.changedAt) from DealStageChange c
      where c.dealId in :dealIds group by c.dealId""")
  List<Object[]> stageEnteredAt(Collection<Long> dealIds);

  /**
   * Who moved each deal into each stage it entered: {@code [dealId, toStageId, changedBy]} rows,
   * one per deal and stage that still exists, from that stage's newest entry ({@code changed_at},
   * then {@code id}); {@code changedBy} is a user id, null when unknown. One query for a page of
   * deals. Native SQL is not tenant-filtered: the workspace is bound here.
   */
  @Query(
      value =
          """
          SELECT DISTINCT ON (deal_id, to_stage_id) deal_id, to_stage_id, changed_by
          FROM deal_stage_changes
          WHERE workspace_id = :workspaceId AND deal_id IN (:dealIds) AND to_stage_id IS NOT NULL
          ORDER BY deal_id, to_stage_id, changed_at DESC, id DESC
          """,
      nativeQuery = true)
  List<Object[]> signatures(long workspaceId, Collection<Long> dealIds);

  /** The time of the workspace's earliest stage change, null without any. */
  @Query("select min(c.changedAt) from DealStageChange c")
  Instant firstChangedAt();
}
