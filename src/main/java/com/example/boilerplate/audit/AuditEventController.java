package com.example.boilerplate.audit;

import com.example.boilerplate.audit.dto.AuditEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/audit-events")
public class AuditEventController {
  private final AuditEventService service;

  public AuditEventController(AuditEventService service) {
    this.service = service;
  }

  @GetMapping
  Page<AuditEventResponse> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) Long actorId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(entityType, entityId, actorId, page, size);
  }
}
