package com.example.boilerplate.crm.deal;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}. */
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
}
