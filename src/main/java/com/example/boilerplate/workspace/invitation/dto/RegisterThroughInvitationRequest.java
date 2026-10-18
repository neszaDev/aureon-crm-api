package com.example.boilerplate.workspace.invitation.dto;

import jakarta.validation.constraints.*;

/** The account's email is the invitation's. A blank {@code displayName} leaves it unset. */
public record RegisterThroughInvitationRequest(
    @NotBlank @Size(min = 6, max = 72) String password, @Size(max = 100) String displayName) {
  // Framework DEBUG logging prints request and response bodies: never the secret.
  @Override
  public String toString() {
    return "RegisterThroughInvitationRequest[password=***, displayName=" + displayName + "]";
  }
}
