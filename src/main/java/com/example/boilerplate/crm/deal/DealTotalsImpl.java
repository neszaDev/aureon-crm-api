package com.example.boilerplate.crm.deal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * A Criteria query, so the tenant filter scopes it like every other query on deals; Spring Data
 * finds it as the {@link DealTotals} fragment of {@link DealRepository} by its name.
 */
class DealTotalsImpl implements DealTotals {
  @PersistenceContext private EntityManager em;

  @Override
  public List<StageTotal> totalsByStage(Specification<Deal> matching) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<Deal> deal = query.from(Deal.class);
    Path<Long> stage = deal.get("stageId");
    Path<String> currency = deal.get("currency");
    query
        .multiselect(stage, currency, cb.count(deal), cb.sum(deal.<BigDecimal>get("amount")))
        .where(matching.toPredicate(deal, query, cb))
        .groupBy(stage, currency)
        .orderBy(cb.asc(currency));
    return em.createQuery(query).getResultList().stream()
        .map(
            row ->
                new StageTotal(
                    row.get(0, Long.class),
                    row.get(1, String.class),
                    row.get(2, Long.class),
                    row.get(3, BigDecimal.class)))
        .toList();
  }
}
