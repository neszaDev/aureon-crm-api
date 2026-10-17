package com.example.boilerplate.crm.task;

import com.example.boilerplate.crm.common.Ordering;
import com.example.boilerplate.crm.common.TextSearch;
import com.example.boilerplate.crm.task.dto.TaskFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/** The task list's filters and special orders as specifications; the tenant filter scopes them. */
final class TaskSpecifications {
  private TaskSpecifications() {}

  /** {@code caller} is what {@code mine} means, {@code now} what overdue is measured against. */
  static Specification<CrmTask> matching(TaskFilter f, long caller, Instant now) {
    String pattern = TextSearch.pattern(f.q()).orElse(null);
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      if (pattern != null) {
        where.add(cb.like(cb.lower(root.get("title")), cb.lower(cb.literal(pattern)), '\\'));
      }
      if (f.statuses() != null && !f.statuses().isEmpty()) {
        where.add(root.get("status").in(f.statuses()));
      }
      if (f.assigneeId() != null) where.add(cb.equal(root.get("assigneeId"), f.assigneeId()));
      if (f.mine()) where.add(cb.equal(root.get("assigneeId"), caller));
      Expression<Instant> due = root.get("dueAt");
      if (f.overdue() != null) {
        Predicate open = cb.equal(root.get("status"), TaskStatus.OPEN);
        where.add(
            f.overdue()
                ? cb.and(open, cb.lessThan(due, now))
                : cb.or(cb.not(open), cb.isNull(due), cb.greaterThanOrEqualTo(due, now)));
      }
      if (f.dueFrom() != null) where.add(cb.greaterThanOrEqualTo(due, f.dueFrom()));
      if (f.dueTo() != null) where.add(cb.lessThanOrEqualTo(due, f.dueTo()));
      if (f.companyId() != null) where.add(cb.equal(root.get("companyId"), f.companyId()));
      if (f.contactId() != null) where.add(cb.equal(root.get("contactId"), f.contactId()));
      if (f.leadId() != null) where.add(cb.equal(root.get("leadId"), f.leadId()));
      if (f.dealId() != null) where.add(cb.equal(root.get("dealId"), f.dealId()));
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  /**
   * The order of the sorts that {@link TaskSort#keys} can't express, to combine with {@link
   * #matching} and query with {@code ListParams.unsorted}; null for the others.
   */
  static Specification<CrmTask> ordering(TaskSort sort, Direction direction) {
    return switch (sort) {
      case DUE_AT -> Ordering.nullsLast("dueAt", direction);
      case PRIORITY -> Ordering.byPriority("priority", direction);
      case CREATED_AT, UPDATED_AT -> null;
    };
  }
}
