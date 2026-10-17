package com.example.boilerplate.crm.deal;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** The board's grouped sums, on the list's own specification ({@link DealTotalsImpl}). */
public interface DealTotals {
  /**
   * One row per stage and currency of the matching deals, by currency: how many and the sum of
   * their amounts, added up as {@code numeric} by the database.
   */
  List<StageTotal> totalsByStage(Specification<Deal> matching);

  record StageTotal(long stageId, String currency, long count, BigDecimal amount) {}
}
