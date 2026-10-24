package com.example.boilerplate.workspace.invitation.dto;

import com.example.boilerplate.workspace.WorkspaceRole;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record CreateInvitationRequest(
    @NotBlank @Email @Size(max = 254) String email, @NotNull WorkspaceRole role) {
  /** Trimmed and lower-cased before validation, as accounts store emails. */
  public CreateInvitationRequest {
    if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
  }
}
