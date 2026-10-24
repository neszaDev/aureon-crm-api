package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.company.dto.*;
import com.example.boilerplate.crm.export.CsvResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/companies")
public class CompanyController {
  private final CompanyService service;

  public CompanyController(CompanyService service) {
    this.service = service;
  }

  @GetMapping
  Page<CompanySummary> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) String industry,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "NAME") CompanySort sort,
      @RequestParam(required = false) Direction direction) {
    return service.list(
        new CompanyFilter(q, ownerId, unassigned, industry, archived),
        page,
        size,
        sort,
        ListParams.direction(direction, sort));
  }

  // Literal segments outrank {id}.
  @CsvResponse
  @GetMapping("/export")
  ResponseEntity<byte[]> export(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) String industry,
      @RequestParam(defaultValue = "false") boolean archived) {
    return service.export(new CompanyFilter(q, ownerId, unassigned, industry, archived)).response();
  }

  @GetMapping("/duplicates")
  List<CompanyRef> duplicates(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String website) {
    return service.duplicates(name, website);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CompanyResponse create(
      @PathVariable long workspaceId, @Valid @RequestBody CreateCompanyRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  CompanyResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  CompanyResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateCompanyRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/archive")
  CompanyResponse archive(@PathVariable long workspaceId, @PathVariable long id) {
    return service.archive(id);
  }

  @PostMapping("/{id}/restore")
  CompanyResponse restore(@PathVariable long workspaceId, @PathVariable long id) {
    return service.restore(id);
  }
}
