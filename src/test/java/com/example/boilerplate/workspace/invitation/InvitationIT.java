package com.example.boilerplate.workspace.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditEvent;
import com.example.boilerplate.audit.AuditEventRepository;
import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class InvitationIT extends WorkspaceTestSupport {
  static final String INVITATIONS = "/api/v1/workspaces/{workspaceId}/invitations";
  static final String LINK = "/api/v1/invitations/{token}";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  @Test
  void inviteAndAcceptAsExistingUser() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String inviteeEmail = uniqueEmail();
    String invitee = tokenFor(inviteeEmail);

    JsonNode created = invite(owner, w, inviteeEmail, "MANAGER");
    assertThat(created.get("email").asText()).isEqualTo(inviteeEmail);
    assertThat(created.get("role").asText()).isEqualTo("MANAGER");
    assertThat(created.get("invitedBy").get("email").asText()).isEqualTo(ownerEmail);
    assertThat(created.get("token").asText()).hasSize(43);
    String link = created.get("token").asText();

    preview(link)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaceId").value(w))
        .andExpect(jsonPath("$.workspaceName").value("Acme"))
        .andExpect(jsonPath("$.email").value(inviteeEmail))
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.status").value("PENDING"));
    send(invitee, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());

    accept(invitee, link).andExpect(status().isOk()).andExpect(jsonPath("$.workspaceId").value(w));

    send(invitee, get("/api/v1/workspaces/{id}", w))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));
    send(owner, get("/api/v1/workspaces/{w}/members", w))
        .andExpect(jsonPath("$[*].email", hasItem(inviteeEmail)));
    send(owner, get(INVITATIONS, w))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].email", not(hasItem(inviteeEmail))));
    preview(link).andExpect(jsonPath("$.status").value("ACCEPTED"));

    long inviteeId = users.findByEmail(inviteeEmail).orElseThrow().getId();
    List<AuditEvent> trail = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w);
    assertThat(trail.get(0).getAction()).isEqualTo(AuditAction.JOIN);
    assertThat(trail.get(0).getEntityType()).isEqualTo(AuditEntityType.MEMBER);
    assertThat(trail.get(0).getEntityId()).isEqualTo(inviteeId);
    assertThat(trail.get(0).getActorEmail()).isEqualTo(inviteeEmail);
    assertRoleSet(trail.get(0), "MANAGER");
    assertThat(trail.get(1).getAction()).isEqualTo(AuditAction.INVITE);
    assertThat(trail.get(1).getEntityType()).isEqualTo(AuditEntityType.INVITATION);
    assertThat(trail.get(1).getEntityLabel()).isEqualTo(inviteeEmail);
    assertThat(trail.get(1).getActorEmail()).isEqualTo(ownerEmail);
    assertRoleSet(trail.get(1), "MANAGER");
  }

  @Test
  void inviteAndRegisterThroughLink() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String newEmail = uniqueEmail();
    String link = invite(owner, w, newEmail, "SALES_REP").get("token").asText();

    JsonNode tokens =
        body(
            register(link, Map.of("password", PASSWORD, "displayName", "  Nok  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").exists()));
    String access = tokens.get("accessToken").asText();

    send(access, get("/api/v1/workspaces"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(w))
        .andExpect(jsonPath("$[0].role").value("SALES_REP"));
    send(access, get("/api/v1/users/me"))
        .andExpect(jsonPath("$.email").value(newEmail))
        .andExpect(jsonPath("$.displayName").value("Nok"));
    preview(link).andExpect(jsonPath("$.status").value("ACCEPTED"));
    login(newEmail, PASSWORD);

    long userId = users.findByEmail(newEmail).orElseThrow().getId();
    AuditEvent join = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).get(0);
    assertThat(join.getAction()).isEqualTo(AuditAction.JOIN);
    assertThat(join.getActorId()).isEqualTo(userId);
    assertThat(join.getEntityId()).isEqualTo(userId);
    assertRoleSet(join, "SALES_REP");
  }

  @Test
  void registerValidatesThePassword() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String newEmail = uniqueEmail();
    String link = invite(owner, w, newEmail, "SALES_REP").get("token").asText();

    register(link, Map.of("password", "short"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
    // 30 Thai characters are 90 UTF-8 bytes: over BCrypt's limit though under @Size's.
    register(link, Map.of("password", "ก".repeat(30)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
    register(link, Map.of("password", PASSWORD, "displayName", "x".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.displayName").exists());

    assertThat(users.findByEmail(newEmail)).isEmpty();
    preview(link).andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void previewShowsExpiredAndRevoked() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    JsonNode revoked = invite(owner, w, uniqueEmail(), "SALES_REP");
    String expiredEmail = uniqueEmail();
    JsonNode expired = invite(owner, w, expiredEmail, "SALES_REP");

    send(owner, delete(INVITATIONS + "/{id}", w, revoked.get("id").asLong()))
        .andExpect(status().isNoContent());
    preview(revoked.get("token").asText()).andExpect(jsonPath("$.status").value("REVOKED"));
    send(owner, delete(INVITATIONS + "/{id}", w, revoked.get("id").asLong()))
        .andExpect(status().isConflict());

    expire(expired.get("id").asLong());
    preview(expired.get("token").asText()).andExpect(jsonPath("$.status").value("EXPIRED"));
    send(owner, get(INVITATIONS, w)).andExpect(jsonPath("$[*].email", not(hasItem(expiredEmail))));
    // An expired invitation doesn't block a new one for the same email.
    invite(owner, w, expiredEmail, "MANAGER");
    preview(expired.get("token").asText()).andExpect(jsonPath("$.status").value("REVOKED"));

    List<AuditAction> actions =
        events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
            .map(AuditEvent::getAction)
            .toList();
    assertThat(actions).contains(AuditAction.INVITE_REVOKE);
  }

  @Test
  void cannotAcceptTwiceOrAfterRevoke() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String inviteeEmail = uniqueEmail();
    String invitee = tokenFor(inviteeEmail);
    String link = invite(owner, w, inviteeEmail, "SALES_REP").get("token").asText();

    accept(invitee, link).andExpect(status().isOk());
    accept(invitee, link).andExpect(status().isConflict());
    register(link, Map.of("password", PASSWORD)).andExpect(status().isConflict());

    String otherEmail = uniqueEmail();
    String other = tokenFor(otherEmail);
    JsonNode revoked = invite(owner, w, otherEmail, "SALES_REP");
    send(owner, delete(INVITATIONS + "/{id}", w, revoked.get("id").asLong()))
        .andExpect(status().isNoContent());
    accept(other, revoked.get("token").asText()).andExpect(status().isConflict());
    send(other, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());

    String lateEmail = uniqueEmail();
    String late = tokenFor(lateEmail);
    JsonNode expired = invite(owner, w, lateEmail, "SALES_REP");
    expire(expired.get("id").asLong());
    accept(late, expired.get("token").asText()).andExpect(status().isConflict());
    send(late, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());
  }

  @Test
  void otherEmailCannotAccept() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String link = invite(owner, w, uniqueEmail(), "ADMIN").get("token").asText();
    String stranger = tokenFor(uniqueEmail());

    accept(stranger, link).andExpect(status().isForbidden());

    send(stranger, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());
    preview(link).andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void registerWithTakenEmailIs409() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String takenEmail = uniqueEmail();
    String taken = tokenFor(takenEmail);
    String link = invite(owner, w, takenEmail, "SALES_REP").get("token").asText();

    register(link, Map.of("password", "another-password"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Email is already registered"));

    send(taken, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());
    login(takenEmail, PASSWORD);
    preview(link).andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void adminCannotInviteOwner() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String adminEmail = uniqueEmail();
    String admin = tokenFor(adminEmail);
    addMember(w, adminEmail, WorkspaceRole.ADMIN);

    send(admin, post(INVITATIONS, w), Map.of("email", uniqueEmail(), "role", "OWNER"))
        .andExpect(status().isForbidden());
    invite(admin, w, uniqueEmail(), "ADMIN");
    // Only owners manage owners: an admin can't revoke an owner's invitation either.
    JsonNode ownerInvite = invite(owner, w, uniqueEmail(), "OWNER");
    send(admin, delete(INVITATIONS + "/{id}", w, ownerInvite.get("id").asLong()))
        .andExpect(status().isForbidden());
    preview(ownerInvite.get("token").asText()).andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void ownerCan() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String coOwnerEmail = uniqueEmail();
    String coOwner = tokenFor(coOwnerEmail);

    String link = invite(owner, w, coOwnerEmail, "OWNER").get("token").asText();
    accept(coOwner, link).andExpect(status().isOk());

    send(coOwner, get("/api/v1/workspaces/{id}", w)).andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void duplicatePendingInviteIs409() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String email = uniqueEmail();
    invite(owner, w, email, "SALES_REP");

    // Emails are compared trimmed and in lower case.
    send(
            owner,
            post(INVITATIONS, w),
            Map.of("email", " " + email.toUpperCase() + " ", "role", "ADMIN"))
        .andExpect(status().isConflict());
    send(owner, post(INVITATIONS, w), Map.of("email", ownerEmail, "role", "ADMIN"))
        .andExpect(status().isConflict());
    send(owner, post(INVITATIONS, w), Map.of("email", "not-an-email", "role", "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    Map<String, Object> noRole = new HashMap<>();
    noRole.put("email", uniqueEmail());
    noRole.put("role", null);
    send(owner, post(INVITATIONS, w), noRole)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.role").exists());

    // The same email may be invited to another workspace.
    long other = createWorkspace(owner, "Other");
    invite(owner, other, email, "SALES_REP");
  }

  @Test
  void tokenIsShownOnceAndStoredHashed() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    JsonNode created = invite(owner, w, uniqueEmail(), "SALES_REP");
    String token = created.get("token").asText();
    long id = created.get("id").asLong();

    JsonNode list = body(send(owner, get(INVITATIONS, w)).andExpect(status().isOk()));
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("id").asLong()).isEqualTo(id);
    assertThat(list.get(0).has("token")).isFalse();
    assertThat(list.get(0).has("tokenHash")).isFalse();
    assertThat(list.get(0).get("expiresAt").asText()).isNotBlank();

    String stored =
        jdbc.queryForObject(
            "SELECT token_hash FROM workspace_invitations WHERE id = ?", String.class, id);
    assertThat(stored).isNotEqualTo(token).isEqualTo(Tokens.sha256Hex(token));
  }

  @Test
  void unknownTokenIs404() throws Exception {
    String user = tokenFor(uniqueEmail());
    String unknown = Tokens.random();

    preview(unknown).andExpect(status().isNotFound());
    accept(user, unknown).andExpect(status().isNotFound());
    register(unknown, Map.of("password", PASSWORD)).andExpect(status().isNotFound());
  }

  /**
   * Error bodies name the route, as logs do: the URI holds the token, which works as a password.
   */
  @Test
  void errorBodiesShowTheRouteNotTheToken() throws Exception {
    String link = Tokens.random();

    // Refused by security, before any route is matched.
    mvc.perform(post(LINK + "/accept", link))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.path").value("/api/v1/invitations/{token}/accept"));
    preview(link)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.path").value("/api/v1/invitations/{token}"));
  }

  @Test
  void invitationIdOfAnotherWorkspaceIs404() throws Exception {
    String ownerA = tokenFor(uniqueEmail());
    long a = createWorkspace(ownerA, "A");
    String ownerB = tokenFor(uniqueEmail());
    long b = createWorkspace(ownerB, "B");
    JsonNode ofB = invite(ownerB, b, uniqueEmail(), "SALES_REP");

    send(ownerA, delete(INVITATIONS + "/{id}", a, ofB.get("id").asLong()))
        .andExpect(status().isNotFound());
    send(ownerA, get(INVITATIONS, b)).andExpect(status().isNotFound());

    preview(ofB.get("token").asText()).andExpect(jsonPath("$.status").value("PENDING"));
  }

  /** Both requests may pass the pending check; the membership's unique key lets one win. */
  @Test
  void concurrentAcceptsCreateOneMembership() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 3; round++) {
        String email = uniqueEmail();
        String invitee = tokenFor(email);
        String link = invite(owner, w, email, "SALES_REP").get("token").asText();
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> acceptIt =
            () -> {
              start.await();
              return accept(invitee, link).andReturn().getResponse().getStatus();
            };
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) results.add(pool.submit(acceptIt));
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> r : results) statuses.add(r.get());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        long userId = users.findByEmail(email).orElseThrow().getId();
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                    Long.class,
                    w,
                    userId))
            .isEqualTo(1L);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /** Logs name the route, never the URI: the path holds the token, which works as a password. */
  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void constraintViolationLogsTheRouteNotTheToken(CapturedOutput output) throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String email = uniqueEmail();
    tokenFor(email);
    JsonNode created = invite(owner, w, email, "OWNER");
    String link = created.get("token").asText();
    // An upper-case copy of a registered email passes the service's check, then the insert of the
    // (lower-cased) account hits users.email's unique key: the handler's constraint-violation log.
    jdbc.update(
        "UPDATE workspace_invitations SET email = upper(email) WHERE id = ?",
        created.get("id").asLong());

    register(link, Map.of("password", PASSWORD)).andExpect(status().isConflict());

    assertThat(output)
        .contains("Constraint violation on POST /api/v1/invitations/{token}/register")
        .doesNotContain(link);
  }

  private static void assertRoleSet(AuditEvent event, String role) {
    Map<String, Object> change = new HashMap<>();
    change.put("from", null);
    change.put("to", role);
    assertThat(event.getChanges()).isEqualTo(Map.of("role", change));
  }

  private JsonNode invite(String token, long workspaceId, String email, String role)
      throws Exception {
    return body(
        send(token, post(INVITATIONS, workspaceId), Map.of("email", email, "role", role))
            .andExpect(status().isCreated()));
  }

  private void expire(long invitationId) {
    jdbc.update(
        "UPDATE workspace_invitations SET expires_at = now() - interval '1 minute' WHERE id = ?",
        invitationId);
  }

  private ResultActions preview(String link) throws Exception {
    return mvc.perform(get(LINK, link));
  }

  private ResultActions accept(String userToken, String link) throws Exception {
    return send(userToken, post(LINK + "/accept", link));
  }

  private ResultActions register(String link, Map<String, String> body) throws Exception {
    return mvc.perform(
        post(LINK + "/register", link)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }
}
