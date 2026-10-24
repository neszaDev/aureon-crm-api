package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.MemberResponse;
import com.example.boilerplate.workspace.dto.UpdateMemberRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class MemberController {
  private final MemberService service;

  public MemberController(MemberService service) {
    this.service = service;
  }

  @GetMapping
  List<MemberResponse> list(@PathVariable long workspaceId) {
    return service.list();
  }

  @PutMapping("/{userId}")
  MemberResponse update(
      @PathVariable long workspaceId,
      @PathVariable long userId,
      @Valid @RequestBody UpdateMemberRequest r) {
    return service.update(userId, r);
  }

  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable long workspaceId, @PathVariable long userId) {
    service.remove(userId);
  }
}
