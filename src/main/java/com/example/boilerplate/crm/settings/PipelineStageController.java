package com.example.boilerplate.crm.settings;

import com.example.boilerplate.crm.settings.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/pipeline-stages")
public class PipelineStageController {
  private final PipelineStageService service;

  public PipelineStageController(PipelineStageService service) {
    this.service = service;
  }

  @GetMapping
  List<StageResponse> list(@PathVariable long workspaceId) {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  StageResponse create(@PathVariable long workspaceId, @Valid @RequestBody CreateStageRequest r) {
    return service.create(r);
  }

  // A literal segment outranks {id}, so this is never taken for an update of stage "order".
  @PutMapping("/order")
  List<StageResponse> reorder(
      @PathVariable long workspaceId, @Valid @RequestBody ReorderStagesRequest r) {
    return service.reorder(r);
  }

  @PutMapping("/{id}")
  StageResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateStageRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable long workspaceId, @PathVariable long id) {
    service.delete(id);
  }
}
