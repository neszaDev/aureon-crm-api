package com.example.boilerplate.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Account security events are one INFO line each, with ids and field names, never the values. The
 * sign-in lockout is in {@code RateLimitIT}, where limits are on.
 */
@ExtendWith(OutputCaptureExtension.class)
class AccountEventLogIT extends WorkspaceTestSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void anAdminsChangeOfEmailAndRoleIsLoggedWithoutTheValues(CapturedOutput out) throws Exception {
    String email = uniqueEmail();
    register(email);
    long target = idOf(email);
    String newEmail = "renamed-" + email;

    send(
            adminAccessToken(),
            patch("/api/v1/users/" + target),
            Map.of("email", newEmail, "role", "ADMIN"))
        .andExpect(status().isOk());

    assertThat(out.getOut())
        .contains(
            "Account event event=account.update actorId="
                + idOf(ADMIN_EMAIL)
                + " targetId="
                + target
                + " fields=[email, role]")
        .doesNotContain(newEmail);
  }

  @Test
  void anAdminsDeletionIsLogged(CapturedOutput out) throws Exception {
    String email = uniqueEmail();
    register(email);
    long target = idOf(email);

    send(adminAccessToken(), delete("/api/v1/users/" + target)).andExpect(status().isNoContent());

    assertThat(out.getOut())
        .contains(
            "Account event event=account.delete actorId="
                + idOf(ADMIN_EMAIL)
                + " targetId="
                + target
                + " fields=[]")
        .doesNotContain(email);
  }

  @Test
  void aPasswordChangeIsLogged(CapturedOutput out) throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long user = idOf(email);

    send(
            token,
            post("/api/v1/users/me/password"),
            Map.of("currentPassword", PASSWORD, "newPassword", "another-password"))
        .andExpect(status().isOk());

    assertThat(out.getOut())
        .contains(
            "Account event event=password.change actorId="
                + user
                + " targetId="
                + user
                + " fields=[password]")
        .doesNotContain("another-password");
  }

  @Test
  void aRefreshTokenReplayThatRevokesASessionIsLogged(CapturedOutput out) throws Exception {
    String email = uniqueEmail();
    String stolen = register(email).get("refreshToken").asText();
    refresh(stolen);
    // Past the 30-second grace.
    jdbc.update(
        "update refresh_tokens set used_at = now() - interval '31 seconds' where token_hash = ?",
        Tokens.sha256Hex(stolen));

    refresh(stolen);

    assertThat(out.getOut())
        .contains(
            "Account event event=refresh.reuse actorId=null targetId=" + idOf(email) + " fields=[]")
        .doesNotContain(stolen);
  }

  private void refresh(String refreshToken) throws Exception {
    mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private long idOf(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }
}
