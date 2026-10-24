package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditEvent;
import com.example.boilerplate.audit.AuditEventRepository;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class MemberIT extends WorkspaceTestSupport {
  static final String MEMBERS = "/api/v1/workspaces/{workspaceId}/members";
  static final String MEMBER = MEMBERS + "/{userId}";

  @Autowired AuditEventRepository events;

  @Test
  void listsMembersByDisplayNameThenEmail() throws Exception {
    String ownerEmail = "b-" + uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String zed = join(w, WorkspaceRole.MANAGER);
    String anna = join(w, WorkspaceRole.SALES_REP);
    String noNameEmail = "a-" + uniqueEmail();
    tokenFor(noNameEmail);
    addMember(w, noNameEmail, WorkspaceRole.SALES_REP);
    send(zed, put("/api/v1/users/me"), Map.of("displayName", "zed"));
    send(anna, put("/api/v1/users/me"), Map.of("displayName", "Anna"));

    send(owner, get(MEMBERS, w))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].displayName", contains("Anna", "zed")))
        .andExpect(jsonPath("$[2].email").value(noNameEmail))
        .andExpect(jsonPath("$[3].email").value(ownerEmail))
        .andExpect(jsonPath("$[3].role").value("OWNER"))
        .andExpect(jsonPath("$[3].userId").value(idOf(ownerEmail)))
        .andExpect(jsonPath("$[3].joinedAt").exists());
  }

  @Test
  void adminCannotChangeOrRemoveOwner() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String admin = join(w, WorkspaceRole.ADMIN);
    long ownerId = idOf(ownerEmail);
    String repEmail = uniqueEmail();
    tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);

    changeRole(admin, w, ownerId, "ADMIN").andExpect(status().isForbidden());
    send(admin, delete(MEMBER, w, ownerId)).andExpect(status().isForbidden());
    changeRole(admin, w, idOf(repEmail), "OWNER").andExpect(status().isForbidden());

    changeRole(admin, w, idOf(repEmail), "MANAGER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.email").value(repEmail));
    send(owner, get("/api/v1/workspaces/{id}", w)).andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void lastOwnerCannotLeaveOrBeDemoted() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    long ownerId = idOf(ownerEmail);

    send(owner, delete(MEMBER, w, ownerId)).andExpect(status().isConflict());
    changeRole(owner, w, ownerId, "ADMIN").andExpect(status().isConflict());

    send(owner, get("/api/v1/workspaces/{id}", w)).andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void secondOwnerThenFirstCanLeave() throws Exception {
    String firstEmail = uniqueEmail();
    String first = tokenFor(firstEmail);
    long w = createWorkspace(first, "Acme");
    String secondEmail = uniqueEmail();
    String second = tokenFor(secondEmail);
    addMember(w, secondEmail, WorkspaceRole.ADMIN);
    long secondId = idOf(secondEmail);

    changeRole(first, w, secondId, "OWNER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("OWNER"));
    AuditEvent roleChange = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).get(0);
    assertThat(roleChange.getAction()).isEqualTo(AuditAction.ROLE_CHANGE);
    assertThat(roleChange.getEntityType()).isEqualTo(AuditEntityType.MEMBER);
    assertThat(roleChange.getEntityId()).isEqualTo(secondId);
    assertThat(roleChange.getEntityLabel()).isEqualTo(secondEmail);
    assertThat(roleChange.getChanges())
        .isEqualTo(Map.of("role", Map.of("from", "ADMIN", "to", "OWNER")));

    send(first, delete(MEMBER, w, idOf(firstEmail))).andExpect(status().isNoContent());

    send(first, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());
    AuditEvent leave = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).get(0);
    assertThat(leave.getAction()).isEqualTo(AuditAction.LEAVE);
    assertThat(leave.getEntityLabel()).isEqualTo(firstEmail);
    // The remaining owner is now the last one.
    send(second, delete(MEMBER, w, secondId)).andExpect(status().isConflict());
  }

  @Test
  void ownerCanDemoteAnotherOwner() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String coOwnerEmail = uniqueEmail();
    tokenFor(coOwnerEmail);
    addMember(w, coOwnerEmail, WorkspaceRole.OWNER);

    changeRole(owner, w, idOf(coOwnerEmail), "SALES_REP")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("SALES_REP"));
  }

  @Test
  void memberCanLeave() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);

    send(rep, delete(MEMBER, w, idOf(repEmail))).andExpect(status().isNoContent());

    send(rep, get("/api/v1/workspaces/{id}", w)).andExpect(status().isNotFound());
    send(owner, get(MEMBERS, w)).andExpect(jsonPath("$[*].email", not(hasItem(repEmail))));
  }

  @Test
  void managerCannotManageMembers() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String manager = join(w, WorkspaceRole.MANAGER);
    String repEmail = uniqueEmail();
    tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long repId = idOf(repEmail);

    send(manager, get(MEMBERS, w)).andExpect(status().isOk());
    changeRole(manager, w, repId, "MANAGER").andExpect(status().isForbidden());
    send(manager, delete(MEMBER, w, repId)).andExpect(status().isForbidden());
    send(manager, get("/api/v1/workspaces/{w}/invitations", w)).andExpect(status().isForbidden());
    send(
            manager,
            post("/api/v1/workspaces/{w}/invitations", w),
            Map.of("email", uniqueEmail(), "role", "SALES_REP"))
        .andExpect(status().isForbidden());

    send(owner, get(MEMBERS, w))
        .andExpect(jsonPath("$[?(@.email == '" + repEmail + "')].role", contains("SALES_REP")));
  }

  @Test
  void invalidRoleIs400() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);

    Map<String, Object> noRole = new HashMap<>();
    noRole.put("role", null);
    send(owner, put(MEMBER, w, idOf(repEmail)), noRole)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.role").exists());
    changeRole(owner, w, idOf(repEmail), "BOSS").andExpect(status().isBadRequest());
  }

  @Test
  void removedMemberLosesAccess() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    send(rep, get(MEMBERS, w)).andExpect(status().isOk());

    send(owner, delete(MEMBER, w, idOf(repEmail))).andExpect(status().isNoContent());

    send(rep, get(MEMBERS, w)).andExpect(status().isNotFound());
    send(owner, delete(MEMBER, w, idOf(repEmail))).andExpect(status().isNotFound());
    AuditEvent removal = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).get(0);
    assertThat(removal.getAction()).isEqualTo(AuditAction.MEMBER_REMOVE);
    assertThat(removal.getEntityId()).isEqualTo(idOf(repEmail));
    assertThat(removal.getEntityLabel()).isEqualTo(repEmail);
  }

  @Test
  void memberIdOfAnotherWorkspaceIs404() throws Exception {
    String ownerA = tokenFor(uniqueEmail());
    long a = createWorkspace(ownerA, "A");
    String ownerBEmail = uniqueEmail();
    String ownerB = tokenFor(ownerBEmail);
    long b = createWorkspace(ownerB, "B");
    String repOfBEmail = uniqueEmail();
    tokenFor(repOfBEmail);
    addMember(b, repOfBEmail, WorkspaceRole.SALES_REP);

    changeRole(ownerA, a, idOf(repOfBEmail), "MANAGER").andExpect(status().isNotFound());
    send(ownerA, delete(MEMBER, a, idOf(repOfBEmail))).andExpect(status().isNotFound());
    send(ownerA, delete(MEMBER, a, idOf(ownerBEmail))).andExpect(status().isNotFound());
    send(ownerA, get(MEMBERS, b)).andExpect(status().isNotFound());

    send(ownerB, get(MEMBERS, b))
        .andExpect(jsonPath("$[?(@.email == '" + repOfBEmail + "')].role", contains("SALES_REP")));
  }

  /** An inviter who leaves, or loses MEMBERS_MANAGE, takes their pending invitations along. */
  @Test
  void removingOrDemotingAMemberRevokesTheInvitationsTheySent() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String removedEmail = uniqueEmail();
    String removed = tokenFor(removedEmail);
    addMember(w, removedEmail, WorkspaceRole.ADMIN);
    String demotedEmail = uniqueEmail();
    String demoted = tokenFor(demotedEmail);
    addMember(w, demotedEmail, WorkspaceRole.ADMIN);
    JsonNode fromRemoved = invite(removed, w);
    JsonNode fromDemoted = invite(demoted, w);
    JsonNode fromOwner = invite(owner, w);

    send(owner, delete(MEMBER, w, idOf(removedEmail))).andExpect(status().isNoContent());
    changeRole(owner, w, idOf(demotedEmail), "MANAGER").andExpect(status().isOk());

    previewStatus(fromRemoved).andExpect(jsonPath("$.status").value("REVOKED"));
    previewStatus(fromDemoted).andExpect(jsonPath("$.status").value("REVOKED"));
    previewStatus(fromOwner).andExpect(jsonPath("$.status").value("PENDING"));
    assertThat(
            events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
                .filter(e -> e.getAction() == AuditAction.INVITE_REVOKE)
                .map(AuditEvent::getEntityId))
        .containsExactlyInAnyOrder(fromRemoved.get("id").asLong(), fromDemoted.get("id").asLong());
  }

  /** An owner demoted to admin can't invite owners any more: those links go, the others stay. */
  @Test
  void anOwnerDemotedToAdminLosesTheOwnerInvitationsTheySent() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String coOwnerEmail = uniqueEmail();
    String coOwner = tokenFor(coOwnerEmail);
    addMember(w, coOwnerEmail, WorkspaceRole.OWNER);
    JsonNode asOwner = invite(coOwner, w, "OWNER");
    JsonNode asRep = invite(coOwner, w, "SALES_REP");

    changeRole(owner, w, idOf(coOwnerEmail), "ADMIN").andExpect(status().isOk());

    previewStatus(asOwner).andExpect(jsonPath("$.status").value("REVOKED"));
    previewStatus(asRep).andExpect(jsonPath("$.status").value("PENDING"));
    assertThat(inviteRevocations(w)).containsExactly(asOwner.get("id").asLong());
  }

  /** A system admin deleting an account revokes its pending invitations in every workspace. */
  @Test
  void deletingAUserRevokesTheInvitationsTheySent() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long first = createWorkspace(owner, "One");
    long second = createWorkspace(owner, "Two");
    String inviterEmail = uniqueEmail();
    String inviter = tokenFor(inviterEmail);
    addMember(first, inviterEmail, WorkspaceRole.ADMIN);
    addMember(second, inviterEmail, WorkspaceRole.ADMIN);
    JsonNode inFirst = invite(inviter, first, "SALES_REP");
    JsonNode inSecond = invite(inviter, second, "MANAGER");
    JsonNode byOwner = invite(owner, first, "SALES_REP");

    send(adminAccessToken(), delete("/api/v1/users/{id}", idOf(inviterEmail)))
        .andExpect(status().isNoContent());

    previewStatus(inFirst).andExpect(jsonPath("$.status").value("REVOKED"));
    previewStatus(inSecond).andExpect(jsonPath("$.status").value("REVOKED"));
    previewStatus(byOwner).andExpect(jsonPath("$.status").value("PENDING"));
    assertThat(inviteRevocations(first)).containsExactly(inFirst.get("id").asLong());
    assertThat(inviteRevocations(second)).containsExactly(inSecond.get("id").asLong());
    // By the system admin, who is no member of either workspace.
    assertThat(
            events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(first).stream()
                .filter(e -> e.getAction() == AuditAction.INVITE_REVOKE)
                .map(AuditEvent::getActorEmail))
        .containsExactly(ADMIN_EMAIL);
  }

  private List<Long> inviteRevocations(long workspaceId) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(workspaceId).stream()
        .filter(e -> e.getAction() == AuditAction.INVITE_REVOKE)
        .map(AuditEvent::getEntityId)
        .toList();
  }

  private JsonNode invite(String token, long workspaceId) throws Exception {
    return invite(token, workspaceId, "SALES_REP");
  }

  private JsonNode invite(String token, long workspaceId, String role) throws Exception {
    return body(
        send(
                token,
                post("/api/v1/workspaces/{w}/invitations", workspaceId),
                Map.of("email", uniqueEmail(), "role", role))
            .andExpect(status().isCreated()));
  }

  private ResultActions previewStatus(JsonNode invitation) throws Exception {
    return mvc.perform(get("/api/v1/invitations/{token}", invitation.get("token").asText()));
  }

  /** Registers a new user, adds them with {@code role} and returns their token. */
  private String join(long workspaceId, WorkspaceRole role) throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    addMember(workspaceId, email, role);
    return token;
  }

  private long idOf(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private ResultActions changeRole(String token, long workspaceId, long userId, String role)
      throws Exception {
    return send(token, put(MEMBER, workspaceId, userId), Map.of("role", role));
  }
}
