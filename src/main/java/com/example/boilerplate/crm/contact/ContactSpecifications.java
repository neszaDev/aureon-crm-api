package com.example.boilerplate.crm.contact;

import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.TextSearch;
import com.example.boilerplate.crm.contact.dto.ContactFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** The contact list's filters as one specification; the workspace comes from the tenant filter. */
final class ContactSpecifications {
  private ContactSpecifications() {}

  static Specification<Contact> matching(ContactFilter f) {
    String pattern = TextSearch.pattern(f.q()).orElse(null);
    String tag = Texts.param("tag", f.tag());
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(
          f.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
      if (pattern != null) {
        Expression<String> p = cb.lower(TextSearch.bound(cb, pattern));
        Expression<String> first = root.get("firstName");
        Expression<String> last = root.get("lastName");
        where.add(
            cb.or(
                contains(cb, first, p),
                contains(cb, last, p),
                contains(cb, cb.concat(cb.concat(first, " "), last), p),
                contains(cb, root.get("email"), p),
                contains(cb, root.get("phone"), p),
                // A left join, the one the company sort reuses: contacts without one still match.
                contains(cb, root.join("company", JoinType.LEFT).get("name"), p)));
      }
      if (f.companyId() != null) where.add(cb.equal(root.get("companyId"), f.companyId()));
      if (f.ownerId() != null) where.add(cb.equal(root.get("ownerId"), f.ownerId()));
      if (f.unassigned()) where.add(cb.isNull(root.get("ownerId")));
      if (f.sourceId() != null) where.add(cb.equal(root.get("sourceId"), f.sourceId()));
      if (tag != null) {
        // shortcut: array_position scans the workspace's contacts (GIN unused); switch tags to
        // varchar(40)[] + array_contains when a workspace reaches tens of thousands of contacts.
        // Hibernate's array_position answers 0, not null, for a missing element.
        where.add(
            cb.greaterThan(
                cb.function(
                    "array_position",
                    Integer.class,
                    root.get("tags"),
                    TextSearch.bound(cb, Contact.tag(tag))),
                0));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  private static Predicate contains(
      CriteriaBuilder cb, Expression<String> text, Expression<String> pattern) {
    return cb.like(cb.lower(text), pattern, '\\');
  }
}
