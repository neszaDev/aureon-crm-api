package com.example.boilerplate.workspace.invitation;

import com.example.boilerplate.workspace.invitation.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/invitations")
public class WorkspaceInvitationController {
  private final InvitationService service;

  public WorkspaceInvitationController(InvitationService service) {
    this.service = service;
  }

  @GetMapping
  List<InvitationResponse> list(@PathVariable long workspaceId) {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedInvitationResponse create(
      @PathVariable long workspaceId, @Valid @RequestBody CreateInvitationRequest r) {
    return service.create(r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(@PathVariable long workspaceId, @PathVariable long id) {
    service.revoke(id);
  }
}
