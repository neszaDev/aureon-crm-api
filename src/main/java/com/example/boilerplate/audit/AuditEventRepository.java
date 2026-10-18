package com.example.boilerplate.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * {@code AuditEvent} is not tenant-filtered, so nothing here may read without a workspace id: no
 * {@code findAll} or {@code findById}, which is why this is a plain {@code Repository}.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {
  AuditEvent save(AuditEvent event);

  /** A page of the workspace's trail, newest first; a null filter matches everything. */
  @Query(
      """
      select e from AuditEvent e
      where e.workspaceId = :workspaceId
        and (:entityType is null or e.entityType = :entityType)
        and (:entityId is null or e.entityId = :entityId)
        and (:actorId is null or e.actorId = :actorId)
      order by e.occurredAt desc, e.id desc""")
  Page<AuditEvent> search(
      long workspaceId, AuditEntityType entityType, Long entityId, Long actorId, Pageable page);

  /** The whole trail of a workspace, unpaged: for tests. */
  List<AuditEvent> findByWorkspaceIdOrderByOccurredAtDescIdDesc(Long workspaceId);
}
