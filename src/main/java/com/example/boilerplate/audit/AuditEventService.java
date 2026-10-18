package com.example.boilerplate.audit;

import com.example.boilerplate.audit.dto.AuditActor;
import com.example.boilerplate.audit.dto.AuditEventResponse;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current workspace's audit trail. */
@Service
@Transactional(readOnly = true)
public class AuditEventService {
  /** The types whose history every member may read (what the record pages show). */
  private static final Set<AuditEntityType> RECORD_TYPES =
      EnumSet.of(
          AuditEntityType.COMPANY,
          AuditEntityType.CONTACT,
          AuditEntityType.LEAD,
          AuditEntityType.DEAL,
          AuditEntityType.TASK,
          AuditEntityType.ACTIVITY);

  private final AuditEventRepository events;
  private final UserRepository users;
  private final WorkspaceAccess access;

  public AuditEventService(
      AuditEventRepository events, UserRepository users, WorkspaceAccess access) {
    this.events = events;
    this.users = users;
    this.access = access;
  }

  /**
   * {@code AUDIT_READ} for the trail, or for any part of it; without it, only the history of one
   * record (both {@code entityType}, a record type, and {@code entityId}), which any member reads.
   * The query is bound to the current workspace: the entity is not tenant-filtered.
   */
  public Page<AuditEventResponse> list(
      AuditEntityType entityType, Long entityId, Long actorId, int page, int size) {
    boolean recordHistory =
        entityId != null && entityType != null && RECORD_TYPES.contains(entityType);
    if (!recordHistory) access.require(Permission.AUDIT_READ);
    Page<AuditEvent> found =
        events.search(
            WorkspaceContext.workspaceId(),
            entityType,
            entityId,
            actorId,
            ListParams.unsorted(page, size));
    Map<Long, String> names = displayNames(found.getContent());
    return found.map(e -> response(e, names));
  }

  /** The current display name of each actor whose account still exists: one query for the page. */
  private Map<Long, String> displayNames(List<AuditEvent> page) {
    List<Long> ids =
        page.stream().map(AuditEvent::getActorId).filter(id -> id != null).distinct().toList();
    Map<Long, String> names = new HashMap<>();
    if (!ids.isEmpty())
      users.findAllById(ids).forEach(u -> names.put(u.getId(), u.getDisplayName()));
    return names;
  }

  private static AuditEventResponse response(AuditEvent e, Map<Long, String> names) {
    boolean hasActor = e.getActorId() != null || e.getActorEmail() != null;
    return new AuditEventResponse(
        e.getId(),
        e.getOccurredAt(),
        hasActor
            ? new AuditActor(e.getActorId(), e.getActorEmail(), names.get(e.getActorId()))
            : null,
        e.getAction(),
        e.getEntityType(),
        e.getEntityId(),
        e.getEntityLabel(),
        e.getChanges());
  }
}
