package com.example.boilerplate.crm.company;

import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.TextSearch;
import com.example.boilerplate.crm.company.dto.CompanyFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** The company list's filters as one specification; the workspace comes from the tenant filter. */
final class CompanySpecifications {
  private CompanySpecifications() {}

  static Specification<Company> matching(CompanyFilter f) {
    String pattern = TextSearch.pattern(f.q()).orElse(null);
    String industry = Texts.param("industry", f.industry());
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(
          f.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
      if (pattern != null) {
        Expression<String> p = cb.lower(cb.literal(pattern));
        where.add(
            cb.or(
                contains(cb, root, "name", p),
                contains(cb, root, "website", p),
                contains(cb, root, "phone", p)));
      }
      if (f.ownerId() != null) where.add(cb.equal(root.get("ownerId"), f.ownerId()));
      if (f.unassigned()) where.add(cb.isNull(root.get("ownerId")));
      if (industry != null) {
        where.add(cb.equal(cb.lower(root.get("industry")), cb.lower(cb.literal(industry))));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  private static Predicate contains(
      CriteriaBuilder cb, Root<Company> root, String attribute, Expression<String> pattern) {
    return cb.like(cb.lower(root.get(attribute)), pattern, '\\');
  }
}
