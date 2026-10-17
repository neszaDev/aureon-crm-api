package com.example.boilerplate.crm.lead;

import com.example.boilerplate.crm.common.Ordering;
import com.example.boilerplate.crm.common.TextSearch;
import com.example.boilerplate.crm.lead.dto.LeadFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/** The lead list's filters and special orders as specifications; the tenant filter scopes them. */
final class LeadSpecifications {
  private LeadSpecifications() {}

  static Specification<Lead> matching(LeadFilter f) {
    String pattern = TextSearch.pattern(f.q()).orElse(null);
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(
          f.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
      if (pattern != null) {
        Expression<String> p = cb.lower(cb.literal(pattern));
        Expression<String> first = root.get("firstName");
        Expression<String> last = root.get("lastName");
        where.add(
            cb.or(
                contains(cb, first, p),
                contains(cb, last, p),
                contains(cb, cb.concat(cb.concat(first, " "), last), p),
                contains(cb, root.get("companyName"), p),
                contains(cb, root.get("email"), p),
                contains(cb, root.get("phone"), p)));
      }
      if (f.statuses() != null && !f.statuses().isEmpty()) {
        where.add(root.get("status").in(f.statuses()));
      }
      if (f.ownerId() != null) where.add(cb.equal(root.get("ownerId"), f.ownerId()));
      if (f.sourceId() != null) where.add(cb.equal(root.get("sourceId"), f.sourceId()));
      if (f.priority() != null) where.add(cb.equal(root.get("priority"), f.priority()));
      if (f.followUpBefore() != null) {
        where.add(cb.lessThanOrEqualTo(root.<LocalDate>get("followUpDate"), f.followUpBefore()));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  /**
   * The order of the sorts that {@link LeadSort#keys} can't express, to combine with {@link
   * #matching} and query with {@code ListParams.unsorted}; null for the others.
   */
  static Specification<Lead> ordering(LeadSort sort, Direction direction) {
    return switch (sort) {
      case FOLLOW_UP_DATE -> Ordering.nullsLast("followUpDate", direction);
      case PRIORITY -> Ordering.byPriority("priority", direction);
      case CREATED_AT, UPDATED_AT, NAME -> null;
    };
  }

  private static Predicate contains(
      CriteriaBuilder cb, Expression<String> text, Expression<String> pattern) {
    return cb.like(cb.lower(text), pattern, '\\');
  }
}
