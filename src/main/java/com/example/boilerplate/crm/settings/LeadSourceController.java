package com.example.boilerplate.crm.settings;

import com.example.boilerplate.crm.settings.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/lead-sources")
public class LeadSourceController {
  private final LeadSourceService service;

  public LeadSourceController(LeadSourceService service) {
    this.service = service;
  }

  @GetMapping
  List<LeadSourceResponse> list(
      @PathVariable long workspaceId,
      @RequestParam(defaultValue = "false") boolean includeArchived) {
    return service.list(includeArchived);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  LeadSourceResponse create(
      @PathVariable long workspaceId, @Valid @RequestBody CreateLeadSourceRequest r) {
    return service.create(r);
  }

  @PutMapping("/{id}")
  LeadSourceResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateLeadSourceRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable long workspaceId, @PathVariable long id) {
    service.delete(id);
  }
}
