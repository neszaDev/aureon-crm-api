package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.TextSearch;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface CompanyRepository
    extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {
  @Query(
      """
      select c from Company c
      where c.archivedAt is null and lower(c.name) = lower(:name)
      order by c.id""")
  List<Company> findActiveByName(String name, Limit limit);

  /**
   * Active companies whose website has {@code host} right after the scheme, with or without {@code
   * www.}: candidates only, since {@code acme.com} also matches {@code acme.community}.
   */
  default List<Company> findWebsiteCandidates(String host) {
    String escaped = TextSearch.escape(host);
    // shortcut: at most 300 candidates, so a host that starts hundreds of websites can miss a real
    // duplicate; store the normalized host in a column of its own when that matters.
    return findActiveByWebsiteLike(
        "%://" + escaped + "%", "%://www." + escaped + "%", Limit.of(300));
  }

  @Query(
      """
      select c from Company c
      where c.archivedAt is null
        and (lower(c.website) like lower(:pattern) escape '\\'
          or lower(c.website) like lower(:wwwPattern) escape '\\')
      order by c.id""")
  List<Company> findActiveByWebsiteLike(String pattern, String wwwPattern, Limit limit);
}
