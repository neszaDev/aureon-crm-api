package com.example.boilerplate.crm.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {
  @Query(
      """
      select s from LeadSource s
      where :includeArchived = true or s.archivedAt is null
      order by lower(s.name), s.id""")
  List<LeadSource> list(boolean includeArchived);

  boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
