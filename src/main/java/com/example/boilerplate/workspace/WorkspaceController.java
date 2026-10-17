package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
  private final WorkspaceService service;

  public WorkspaceController(WorkspaceService service) {
    this.service = service;
  }

  @GetMapping
  List<WorkspaceSummaryResponse> mine(Authentication a) {
    return service.mine(a.getName());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceResponse create(Authentication a, @Valid @RequestBody CreateWorkspaceRequest r) {
    return service.create(a.getName(), r);
  }

  // workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
  @GetMapping("/{workspaceId}")
  WorkspaceResponse get(@PathVariable long workspaceId) {
    return service.get();
  }

  @PutMapping("/{workspaceId}")
  WorkspaceResponse update(
      @PathVariable long workspaceId, @Valid @RequestBody UpdateWorkspaceRequest r) {
    return service.update(r);
  }
}
