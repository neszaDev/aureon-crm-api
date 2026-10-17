package com.example.boilerplate.crm.activity;

import com.example.boilerplate.crm.activity.dto.*;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/activities")
public class ActivityController {
  private final ActivityService service;

  public ActivityController(ActivityService service) {
    this.service = service;
  }

  @GetMapping
  Page<ActivityResponse> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) ActivityType type,
      @RequestParam(required = false) Long companyId,
      @RequestParam(required = false) Long contactId,
      @RequestParam(required = false) Long leadId,
      @RequestParam(required = false) Long dealId,
      @RequestParam(required = false) Long authorId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(
        new ActivityFilter(type, companyId, contactId, leadId, dealId, authorId, from, to),
        page,
        size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ActivityResponse create(
      @PathVariable long workspaceId, @Valid @RequestBody CreateActivityRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  ActivityResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  ActivityResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateActivityRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable long workspaceId, @PathVariable long id) {
    service.delete(id);
  }
}
