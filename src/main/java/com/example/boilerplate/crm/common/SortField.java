package com.example.boilerplate.crm.common;

import java.util.List;
import org.springframework.data.domain.Sort.Direction;

/**
 * A sort choice of a list endpoint; each record type has an enum of these (query: {@code sort}).
 */
public interface SortField {
  /**
   * What the list is ordered by, first key first, all in the request's direction; {@link
   * ListParams} adds the id tie-breaker.
   */
  List<Key> keys();

  /**
   * The direction when the request names none: the natural one of the sort, {@code DESC} for dates
   * (newest first) and a priority (most urgent first), {@code ASC} otherwise.
   */
  Direction defaultDirection();

  /**
   * An entity attribute, or a path through a to-one link ({@code company.name}: Spring Data
   * left-joins it, so rows without the link stay in the list). {@code ignoreCase} sorts text by its
   * lower case, so "beta" lands between "Alpha" and "Gamma" whatever the database collation
   * (Alpine's PostgreSQL sorts upper case first). Null values keep PostgreSQL's place for them:
   * last ascending, first descending.
   */
  record Key(String property, boolean ignoreCase) {
    public static Key of(String property) {
      return new Key(property, false);
    }

    public static Key ignoringCase(String property) {
      return new Key(property, true);
    }
  }
}
