package com.example.boilerplate.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.auth.AuthProperties;
import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.auth.dto.LoginRequest;
import com.example.boilerplate.auth.dto.RefreshTokenRequest;
import com.example.boilerplate.auth.dto.RegisterRequest;
import com.example.boilerplate.auth.dto.TokenResponse;
import com.example.boilerplate.crm.demo.DemoProperties;
import com.example.boilerplate.user.AdminProperties;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.invitation.dto.CreatedInvitationResponse;
import com.example.boilerplate.workspace.invitation.dto.RegisterThroughInvitationRequest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Records holding a password, token or secret leave it out of {@code toString()}: framework DEBUG
 * logging prints request bodies ({@code Read "application/json" to [LoginRequest[...]]}).
 */
class SecretRedactionTest {
  static final String SECRET = "s3cret-value-never-logged";

  @Test
  void authRequestsAndResponses() {
    assertRedacted(new LoginRequest("me@example.com", SECRET), "me@example.com");
    assertRedacted(new RegisterRequest("me@example.com", SECRET), "me@example.com");
    assertRedacted(new RefreshTokenRequest(SECRET), "RefreshTokenRequest");
    assertRedacted(new AuthTokensResponse(SECRET, "Bearer", 900, SECRET, 60), "Bearer");
    assertRedacted(new TokenResponse(SECRET, "Bearer", 900), "Bearer");
  }

  @Test
  void passwordChangeAndInvitationRecords() {
    assertRedacted(new ChangePasswordRequest(SECRET, SECRET), "ChangePasswordRequest");
    assertRedacted(new RegisterThroughInvitationRequest(SECRET, "Mo"), "Mo");
    Instant now = Instant.now();
    assertRedacted(
        new CreatedInvitationResponse(
            1, "x@example.com", WorkspaceRole.ADMIN, null, now, now, SECRET),
        "x@example.com");
  }

  @Test
  void configurationRecords() {
    assertRedacted(
        new AuthProperties(
            new AuthProperties.Jwt(SECRET, Duration.ofMinutes(15)), Duration.ofDays(30), true),
        "PT15M");
    assertRedacted(new AdminProperties("admin@example.com", SECRET), "admin@example.com");
    assertRedacted(new DemoProperties(true, SECRET), "true");
  }

  /** The secret is gone, the rest (here {@code kept}) still helps debugging. */
  private static void assertRedacted(Object record, String kept) {
    assertThat(record.toString()).doesNotContain(SECRET).contains(kept);
  }
}
