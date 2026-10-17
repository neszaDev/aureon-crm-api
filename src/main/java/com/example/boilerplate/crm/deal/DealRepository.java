package com.example.boilerplate.crm.deal;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface DealRepository
    extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal>, DealTotals {
  /** Archived deals included: their foreign key holds the stage too. */
  boolean existsByStageId(long stageId);

  /** Archived deals included. */
  boolean existsBySourceId(long sourceId);

  /** Rows of (stage id, number of active deals) for the stages that have any. */
  @Query(
      """
      select d.stageId, count(d) from Deal d
      where d.archivedAt is null
      group by d.stageId""")
  List<Object[]> countActiveByStage();

  /** Rows of (company id, number of active deals in an open stage) for the companies with any. */
  @Query(
      """
      select d.companyId, count(d) from Deal d join d.stage s
      where d.companyId in :companyIds and d.archivedAt is null
        and s.kind = com.example.boilerplate.crm.settings.StageKind.OPEN
      group by d.companyId""")
  List<Object[]> countOpenByCompany(Collection<Long> companyIds);
}
