package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.common.Ordering;
import com.example.boilerplate.crm.common.TextSearch;
import com.example.boilerplate.crm.deal.dto.DealFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/** The deal list's filters and special order as specifications; the tenant filter scopes them. */
final class DealSpecifications {
  private DealSpecifications() {}

  static Specification<Deal> matching(DealFilter f) {
    String pattern = TextSearch.pattern(f.q()).orElse(null);
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(
          f.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
      if (pattern != null) {
        Expression<String> p = cb.lower(cb.literal(pattern));
        where.add(
            cb.or(
                contains(cb, root.get("name"), p),
                // A left join: deals without a company still match by name.
                contains(cb, root.join("company", JoinType.LEFT).get("name"), p)));
      }
      if (f.stageId() != null) where.add(cb.equal(root.get("stageId"), f.stageId()));
      if (f.status() != null) {
        where.add(cb.equal(root.join("stage").get("kind"), f.status().kind()));
      }
      if (f.ownerId() != null) where.add(cb.equal(root.get("ownerId"), f.ownerId()));
      if (f.unassigned()) where.add(cb.isNull(root.get("ownerId")));
      if (f.companyId() != null) where.add(cb.equal(root.get("companyId"), f.companyId()));
      if (f.contactId() != null) where.add(cb.equal(root.get("contactId"), f.contactId()));
      if (f.closeFrom() != null) {
        where.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("expectedCloseDate"), f.closeFrom()));
      }
      if (f.closeTo() != null) {
        where.add(cb.lessThanOrEqualTo(root.<LocalDate>get("expectedCloseDate"), f.closeTo()));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  /**
   * The order of the sort that {@link DealSort#keys} can't express, to combine with {@link
   * #matching} and query with {@code ListParams.unsorted}; null for the others.
   */
  static Specification<Deal> ordering(DealSort sort, Direction direction) {
    return switch (sort) {
      case EXPECTED_CLOSE_DATE -> Ordering.nullsLast("expectedCloseDate", direction);
      case UPDATED_AT, CREATED_AT, AMOUNT, NAME -> null;
    };
  }

  private static Predicate contains(
      CriteriaBuilder cb, Expression<String> text, Expression<String> pattern) {
    return cb.like(cb.lower(text), pattern, '\\');
  }
}
