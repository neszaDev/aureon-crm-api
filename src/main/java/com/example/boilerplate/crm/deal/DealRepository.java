package com.example.boilerplate.crm.deal;

import java.time.Instant;
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

  /**
   * Rows of (stage id, currency, count, sum of amounts, sum of amount × probability) of the active
   * deals in open stages, by currency. The probability is the deal's, else its stage's, as in the
   * deal's {@code probability}; the sums are {@code numeric}.
   */
  @Query(
      """
      select d.stageId, d.currency, count(d), sum(d.amount),
        sum(d.amount * coalesce(d.probability, s.probability))
      from Deal d join d.stage s
      where d.archivedAt is null and s.kind = com.example.boilerplate.crm.settings.StageKind.OPEN
      group by d.stageId, d.currency
      order by d.currency""")
  List<Object[]> openTotals();

  /**
   * Rows of (owner id, null when unassigned; currency, count, sum of amounts) of the active deals
   * in open stages, by currency.
   */
  @Query(
      """
      select d.ownerId, d.currency, count(d), sum(d.amount)
      from Deal d join d.stage s
      where d.archivedAt is null and s.kind = com.example.boilerplate.crm.settings.StageKind.OPEN
      group by d.ownerId, d.currency
      order by d.currency""")
  List<Object[]> openTotalsByOwner();

  /**
   * Rows of (stage kind, currency, count, sum of amounts) of the active deals in the WON or LOST
   * stage closed from {@code start} (inclusive) to {@code end} (exclusive), by currency.
   */
  @Query(
      """
      select s.kind, d.currency, count(d), sum(d.amount)
      from Deal d join d.stage s
      where d.archivedAt is null and s.kind <> com.example.boilerplate.crm.settings.StageKind.OPEN
        and d.closedAt >= :start and d.closedAt < :end
      group by s.kind, d.currency
      order by d.currency""")
  List<Object[]> closedTotals(Instant start, Instant end);
}
