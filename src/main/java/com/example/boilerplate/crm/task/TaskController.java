package com.example.boilerplate.crm.task;

import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.task.dto.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
public class TaskController {
  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @GetMapping
  Page<TaskResponse> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<TaskStatus> status,
      @RequestParam(required = false) Long assigneeId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(required = false) Instant dueFrom,
      @RequestParam(required = false) Instant dueTo,
      @RequestParam(required = false) Long companyId,
      @RequestParam(required = false) Long contactId,
      @RequestParam(required = false) Long leadId,
      @RequestParam(required = false) Long dealId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "DUE_AT") TaskSort sort,
      @RequestParam(required = false) Direction direction) {
    return service.list(
        new TaskFilter(
            q,
            status,
            assigneeId,
            unassigned,
            mine,
            overdue,
            dueFrom,
            dueTo,
            companyId,
            contactId,
            leadId,
            dealId),
        page,
        size,
        sort,
        ListParams.direction(direction, sort));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TaskResponse create(@PathVariable long workspaceId, @Valid @RequestBody CreateTaskRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  TaskResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  TaskResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateTaskRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/complete")
  TaskResponse complete(
      @PathVariable long workspaceId, @PathVariable long id, @Valid @RequestBody VersionRequest r) {
    return service.complete(id, r);
  }

  @PostMapping("/{id}/reopen")
  TaskResponse reopen(
      @PathVariable long workspaceId, @PathVariable long id, @Valid @RequestBody VersionRequest r) {
    return service.reopen(id, r);
  }

  @PostMapping("/{id}/cancel")
  TaskResponse cancel(
      @PathVariable long workspaceId, @PathVariable long id, @Valid @RequestBody VersionRequest r) {
    return service.cancel(id, r);
  }
}
