package com.example.boilerplate.crm.activity;

import com.example.boilerplate.crm.activity.dto.ActivityFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** The activity list's filters as a specification; the tenant filter scopes it. */
final class ActivitySpecifications {
  private ActivitySpecifications() {}

  static Specification<Activity> matching(ActivityFilter f) {
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      if (f.type() != null) where.add(cb.equal(root.get("type"), f.type()));
      if (f.companyId() != null) where.add(cb.equal(root.get("companyId"), f.companyId()));
      if (f.contactId() != null) where.add(cb.equal(root.get("contactId"), f.contactId()));
      if (f.leadId() != null) where.add(cb.equal(root.get("leadId"), f.leadId()));
      if (f.dealId() != null) where.add(cb.equal(root.get("dealId"), f.dealId()));
      if (f.authorId() != null) where.add(cb.equal(root.get("authorId"), f.authorId()));
      if (f.from() != null) where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
      if (f.to() != null) where.add(cb.lessThan(root.get("occurredAt"), f.to()));
      return cb.and(where.toArray(Predicate[]::new));
    };
  }
}
