package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.deal.dto.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/deals")
public class DealController {
  private final DealService service;
  private final DealBoardService board;

  public DealController(DealService service, DealBoardService board) {
    this.service = service;
    this.board = board;
  }

  @GetMapping
  Page<DealSummary> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long stageId,
      @RequestParam(required = false) DealStatus status,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Long companyId,
      @RequestParam(required = false) Long contactId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate closeFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate closeTo,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "UPDATED_AT") DealSort sort,
      @RequestParam(required = false) Direction direction) {
    return service.list(
        new DealFilter(
            q,
            stageId,
            status,
            ownerId,
            unassigned,
            companyId,
            contactId,
            closeFrom,
            closeTo,
            archived),
        page,
        size,
        sort,
        ListParams.direction(direction, sort));
  }

  // A literal segment outranks {id}.
  @GetMapping("/board")
  BoardResponse board(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) Long companyId) {
    return board.board(q, ownerId, companyId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DealResponse create(@PathVariable long workspaceId, @Valid @RequestBody CreateDealRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  DealResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  DealResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateDealRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/move")
  DealResponse move(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody MoveDealRequest r) {
    return service.move(id, r);
  }

  @GetMapping("/{id}/history")
  List<StageChangeResponse> history(@PathVariable long workspaceId, @PathVariable long id) {
    return service.history(id);
  }

  @PostMapping("/{id}/archive")
  DealResponse archive(@PathVariable long workspaceId, @PathVariable long id) {
    return service.archive(id);
  }

  @PostMapping("/{id}/restore")
  DealResponse restore(@PathVariable long workspaceId, @PathVariable long id) {
    return service.restore(id);
  }
}
