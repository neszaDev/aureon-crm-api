package com.example.boilerplate.workspace.invitation;

import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.workspace.invitation.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Using a link. Preview and register are public (SecurityConfig); accepting needs a sign-in. */
@RestController
@RequestMapping("/api/v1/invitations/{token}")
public class InvitationController {
  private final InvitationService service;

  public InvitationController(InvitationService service) {
    this.service = service;
  }

  @GetMapping
  InvitationPreviewResponse preview(@PathVariable String token) {
    return service.preview(token);
  }

  @PostMapping("/accept")
  AcceptInvitationResponse accept(@PathVariable String token, Authentication a) {
    return service.accept(token, a.getName());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthTokensResponse register(
      @PathVariable String token,
      @Valid @RequestBody RegisterThroughInvitationRequest r,
      HttpServletRequest req) {
    return service.register(token, r, req.getRemoteAddr());
  }
}
