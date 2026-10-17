package com.example.boilerplate.audit;

import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only trail. MANDATORY: an event commits or rolls back with the change it
 * describes, so a caller without a transaction is a bug.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AuditTrail {
  private static final int LABEL_MAX = 200;
  // The request id is client-supplied (X-Request-Id); a longer one must not fail the request.
  private static final int REQUEST_ID_MAX = 64;

  private final AuditEventRepository events;

  public AuditTrail(AuditEventRepository events) {
    this.events = events;
  }

  /** Inside a workspace request: workspace and actor come from the current membership. */
  public void record(
      AuditAction action,
      AuditEntityType type,
      long entityId,
      String label,
      Map<String, ?> changes) {
    Membership m = WorkspaceContext.require();
    record(m.workspaceId(), m.userId(), m.email(), action, type, entityId, label, changes);
  }

  /** Outside a workspace context: workspace creation, invitation acceptance. */
  public void record(
      long workspaceId,
      long actorUserId,
      String actorEmail,
      AuditAction action,
      AuditEntityType type,
      long entityId,
      String label,
      Map<String, ?> changes) {
    events.save(
        new AuditEvent(
            workspaceId,
            Instant.now(),
            actorUserId,
            actorEmail,
            action,
            type,
            entityId,
            truncate(label, LABEL_MAX),
            new LinkedHashMap<>(changes),
            truncate(MDC.get("requestId"), REQUEST_ID_MAX)));
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}
