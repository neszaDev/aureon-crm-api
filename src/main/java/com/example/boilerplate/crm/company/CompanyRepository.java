package com.example.boilerplate.crm.company;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface CompanyRepository
    extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {
  /**
   * Active companies named {@code name} (any case) or whose website matches {@code websitePattern}
   * (a {@code LIKE} pattern, escaped with {@code \}); a null argument matches nothing.
   */
  @Query(
      """
      select c from Company c
      where c.archivedAt is null
        and (lower(c.name) = lower(:name)
          or lower(c.website) like lower(:websitePattern) escape '\\')
      order by c.id""")
  List<Company> findDuplicateCandidates(String name, String websitePattern);
}
