package com.example.boilerplate.crm.contact;

import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.contact.dto.*;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/contacts")
public class ContactController {
  private final ContactService service;

  public ContactController(ContactService service) {
    this.service = service;
  }

  @GetMapping
  Page<ContactSummary> list(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long companyId,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Long sourceId,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "NAME") ContactSort sort,
      @RequestParam(required = false) Direction direction) {
    return service.list(
        new ContactFilter(q, companyId, ownerId, unassigned, sourceId, tag, archived),
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
      @RequestParam(required = false) Long companyId,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(required = false) Long sourceId,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "false") boolean archived) {
    return service
        .export(new ContactFilter(q, companyId, ownerId, unassigned, sourceId, tag, archived))
        .response();
  }

  @GetMapping("/tags")
  List<String> tags(@PathVariable long workspaceId) {
    return service.tags();
  }

  @GetMapping("/duplicates")
  List<ContactRef> duplicates(
      @PathVariable long workspaceId,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String phone) {
    return service.duplicates(email, phone);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ContactResponse create(
      @PathVariable long workspaceId, @Valid @RequestBody CreateContactRequest r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  ContactResponse get(@PathVariable long workspaceId, @PathVariable long id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  ContactResponse update(
      @PathVariable long workspaceId,
      @PathVariable long id,
      @Valid @RequestBody UpdateContactRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/archive")
  ContactResponse archive(@PathVariable long workspaceId, @PathVariable long id) {
    return service.archive(id);
  }

  @PostMapping("/{id}/restore")
  ContactResponse restore(@PathVariable long workspaceId, @PathVariable long id) {
    return service.restore(id);
  }
}
