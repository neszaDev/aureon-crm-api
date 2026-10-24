package com.example.boilerplate.crm.contact;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, except for the native {@link #tags}. */
public interface ContactRepository
    extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {
  long countByArchivedAtIsNull();

  @Query(
      """
      select c from Contact c
      where c.archivedAt is null and lower(c.email) = lower(:email)
      order by c.id""")
  List<Contact> findActiveByEmail(String email, Limit limit);

  /** Active contacts whose phone has exactly these digits once everything else is removed. */
  @Query(
      """
      select c from Contact c
      where c.archivedAt is null
        and function('regexp_replace', c.phone, '[^0-9]', '', 'g') = :digits
      order by c.id""")
  List<Contact> findActiveByPhoneDigits(String digits, Limit limit);

  /** Rows of (company id, number of active contacts) for those of the companies that have any. */
  @Query(
      """
      select c.companyId, count(c) from Contact c
      where c.companyId in :companyIds and c.archivedAt is null
      group by c.companyId""")
  List<Object[]> countActiveByCompany(Collection<Long> companyIds);

  /** Archived contacts included. */
  boolean existsBySourceId(long sourceId);

  /**
   * The distinct tags of the workspace's active contacts in code point order. Native SQL is not
   * tenant-filtered: the workspace is bound here.
   */
  @Query(
      value =
          """
          SELECT tag FROM contacts, unnest(tags) AS tag
          WHERE workspace_id = :workspaceId AND archived_at IS NULL
          GROUP BY tag
          ORDER BY tag COLLATE "C"
          """,
      nativeQuery = true)
  List<String> tags(long workspaceId);
}
