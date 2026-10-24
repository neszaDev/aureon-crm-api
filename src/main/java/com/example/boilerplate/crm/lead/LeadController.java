package com.example.boilerplate.crm.lead;

import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.export.CsvResponse;
import com.example.boilerplate.crm.lead.dto.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/leads")
public class LeadController {
  private final LeadService service;
  private final LeadConversionService conversions;

  public LeadController(LeadService service, LeadConversionService conversions) {
    this.service = service;
    this.conversions = conversions;
  }

  @GetMapping
  Page<LeadResponse> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<LeadStatus> status,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Long sourceId,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate followUpBefore,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "CREATED_AT") LeadSort sort,
      @RequestParam(required = false) Direction direction) {
    return service.list(
        new LeadFilter(
            q, status, ownerId, unassigned, sourceId, priority, followUpBefore, archived),
        page,
        size,
        sort,
        ListParams.direction(direction, sort));
  }

  // A literal segment outranks {id}.
  @CsvResponse
  @GetMapping("/export")
  ResponseEntity<byte[]> export(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<LeadStatus> status,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Long sourceId,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate followUpBefore,
      @RequestParam(defaultValue = "false") boolean archived) {
    return service
        .export(
            new LeadFilter(
                q, status, ownerId, unassigned, sourceId, priority, followUpBefore, archived))
        .response();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  LeadResponse create(@PathVariable long workspaceId, @Valid @RequestBody CreateLeadRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  LeadResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  LeadResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateLeadRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/status")
  LeadResponse changeStatus(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody LeadStatusRequest r) {
    return service.changeStatus(id, r);
  }

  @PostMapping("/{id}/convert")
  LeadConversionResponse convert(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody LeadConversionInput r) {
    return conversions.convert(id, r);
  }

  @PostMapping("/{id}/archive")
  LeadResponse archive(@PathVariable long workspaceId, @PathVariable long id) {
    return service.archive(id);
  }

  @PostMapping("/{id}/restore")
  LeadResponse restore(@PathVariable long workspaceId, @PathVariable long id) {
    return service.restore(id);
  }
}
