package com.example.boilerplate.crm.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TaskIT extends WorkspaceTestSupport {
  static final String TASKS = "/api/v1/workspaces/{workspaceId}/tasks";
  static final String TASK = TASKS + "/{id}";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  /** A signed-in member of a workspace. */
  record Member(String email, String token, long userId) {}

  @Test
  void defaultsToCallerAsAssignee() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");

    long id =
        body(send(token, post(TASKS, w), Map.of("title", " Call back　"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Call back"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.dueAt").doesNotExist())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.assignee.userId").value(userId(email)))
                .andExpect(jsonPath("$.assignee.email").value(email))
                .andExpect(jsonPath("$.createdBy.userId").value(userId(email)))
                .andExpect(jsonPath("$.createdBy.email").value(email))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.lead").doesNotExist())
                .andExpect(jsonPath("$.deal").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();

    // An explicit null is the same as leaving it out.
    Map<String, Object> nullAssignee = new HashMap<>();
    nullAssignee.put("title", "Nobody named");
    nullAssignee.put("assigneeId", null);
    send(token, post(TASKS, w), nullAssignee)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assignee.userId").value(userId(email)));

    // Every create writes one CREATE event with no changes and the title as its label.
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Call back");
    assertThat(trail.get(0).getChanges()).isEmpty();
  }

  @Test
  void createGetUpdateWithLinks() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long company = id(send(token, post(COMPANIES, w), Map.of("name", "Initech")));
    long contact =
        id(send(token, post(CONTACTS, w), Map.of("firstName", "Peter", "lastName", "Gibbons")));
    long lead = id(send(token, post(LEADS, w), Map.of("firstName", "Ada", "lastName", "Lovelace")));
    long deal = id(send(token, post(DEALS, w), Map.of("name", "TPS reports", "amount", "1")));
    Instant due = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Map<String, Object> input = new HashMap<>();
    input.put("title", "Prepare the proposal");
    input.put("description", " Include the TPS cover sheet ");
    input.put("dueAt", due.toString());
    input.put("priority", "HIGH");
    input.put("assigneeId", userId(email));
    input.put("companyId", company);
    input.put("contactId", contact);
    input.put("leadId", lead);
    input.put("dealId", deal);

    long id =
        body(send(token, post(TASKS, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Include the TPS cover sheet"))
                .andExpect(jsonPath("$.dueAt").value(due.toString()))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.company.id").value(company))
                .andExpect(jsonPath("$.company.name").value("Initech"))
                .andExpect(jsonPath("$.contact.id").value(contact))
                .andExpect(jsonPath("$.contact.fullName").value("Peter Gibbons"))
                .andExpect(jsonPath("$.lead.id").value(lead))
                .andExpect(jsonPath("$.lead.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.deal.id").value(deal))
                .andExpect(jsonPath("$.deal.name").value("TPS reports")))
            .get("id")
            .asLong();

    send(token, get(TASK, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Prepare the proposal"))
        .andExpect(jsonPath("$.lead.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.version").value(0));
    // The list returns full tasks, links included.
    list(token, w)
        .andExpect(jsonPath("$.content[0].title").value("Prepare the proposal"))
        .andExpect(jsonPath("$.content[0].description").value("Include the TPS cover sheet"))
        .andExpect(jsonPath("$.content[0].company.name").value("Initech"))
        .andExpect(jsonPath("$.content[0].contact.fullName").value("Peter Gibbons"))
        .andExpect(jsonPath("$.content[0].lead.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.content[0].deal.name").value("TPS reports"))
        .andExpect(jsonPath("$.content[0].assignee.email").value(email))
        .andExpect(jsonPath("$.content[0].createdBy.email").value(email));

    // Omitted optional fields are cleared (the priority falls back to MEDIUM); the assignee is
    // kept by sending it.
    Map<String, Object> edit = edit("Prepare the proposal", userId(email), 0);
    send(token, put(TASK, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.dueAt").doesNotExist())
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.lead").doesNotExist())
        .andExpect(jsonPath("$.deal").doesNotExist())
        .andExpect(jsonPath("$.assignee.userId").value(userId(email)))
        .andExpect(jsonPath("$.createdBy.userId").value(userId(email)))
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(TASK, w, id), edit).andExpect(jsonPath("$.version").value(1));
    // A stale version is a 409 that writes nothing.
    edit.put("version", 0);
    edit.put("title", "Lost update");
    send(token, put(TASK, w, id), edit).andExpect(status().isConflict());
    send(token, get(TASK, w, id)).andExpect(jsonPath("$.title").value("Prepare the proposal"));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys("description", "dueAt", "priority", "company", "contact", "lead", "deal");
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Prepare the proposal");
  }

  @Test
  void validation() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");

    invalid(token, w, Map.of(), "title");
    invalid(token, w, Map.of("title", "　 "), "title");
    invalid(token, w, Map.of("title", "x".repeat(201)), "title");
    invalid(token, w, Map.of("title", "x", "description", "x".repeat(5001)), "description");
    malformed(token, w, Map.of("title", "x", "priority", "URGENT"));
    malformed(token, w, Map.of("title", "x", "dueAt", "tomorrow"));
    malformed(token, w, Map.of("title", "x", "dueAt", "2026-02-30T10:00:00Z"));
    malformed(token, w, Map.of("title", "x", "assigneeId", "me"));
    // Exactly at the limits.
    send(token, post(TASKS, w), Map.of("title", "x".repeat(200), "description", "d".repeat(5000)))
        .andExpect(status().isCreated());

    long id = create(token, w, Map.of("title", "Task"));
    send(token, put(TASK, w, id), Map.of("title", "Task"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    send(token, put(TASK, w, id), edit("　", userId(email), 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.title").exists());
    for (String action : List.of("complete", "reopen", "cancel")) {
      send(token, post(TASK + "/" + action, w, id), Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors.version").exists());
    }
    list(token, w).andExpect(jsonPath("$.totalElements").value(2));
    send(token, get(TASK, w, id))
        .andExpect(jsonPath("$.title").value("Task"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.version").value(0));
  }

  /** Due times are instants, stored in microseconds: the answer equals a later read. */
  @Test
  void dueAtKeepsMicrosecondsAsStored() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    String sent = "2031-03-04T10:15:30.123456789Z";

    long id =
        body(send(token, post(TASKS, w), Map.of("title", "Precise", "dueAt", sent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueAt").value("2031-03-04T10:15:30.123456Z")))
            .get("id")
            .asLong();
    send(token, get(TASK, w, id))
        .andExpect(jsonPath("$.dueAt").value("2031-03-04T10:15:30.123456Z"));

    // The nanoseconds are not a change: same version, no UPDATE event.
    send(token, put(TASK, w, id), edit("Precise", userId(email), 0, "dueAt", sent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    assertThat(trail(w, id)).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);

    // An offset names the same instant.
    send(
            token,
            put(TASK, w, id),
            edit("Precise", userId(email), 0, "dueAt", "2031-03-04T11:15:30.123456+01:00"))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void repCannotAssignOthersAndManagerCan() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    Member rep = member(w, WorkspaceRole.SALES_REP);
    Member other = member(w, WorkspaceRole.SALES_REP);
    Member manager = member(w, WorkspaceRole.MANAGER);
    Member outsider = outsider();

    // A rep creates for themselves, by default or by naming themselves, not for anyone else.
    send(rep.token(), post(TASKS, w), Map.of("title", "Mine", "assigneeId", rep.userId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assignee.userId").value(rep.userId()));
    send(rep.token(), post(TASKS, w), Map.of("title", "Gift", "assigneeId", other.userId()))
        .andExpect(status().isForbidden());
    send(rep.token(), post(TASKS, w), Map.of("title", "Gift", "assigneeId", manager.userId()))
        .andExpect(status().isForbidden());
    list(rep.token(), w).andExpect(jsonPath("$.totalElements").value(1));

    long own = create(rep.token(), w, Map.of("title", "Rep's own"));
    // Keeping the assignee needs no permission; reassigning or unassigning does.
    send(rep.token(), put(TASK, w, own), edit("Renamed", rep.userId(), 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Renamed"));
    send(rep.token(), put(TASK, w, own), edit("Renamed", other.userId(), 1))
        .andExpect(status().isForbidden());
    send(rep.token(), put(TASK, w, own), edit("Renamed", null, 1))
        .andExpect(status().isForbidden());
    send(rep.token(), get(TASK, w, own))
        .andExpect(jsonPath("$.assignee.userId").value(rep.userId()))
        .andExpect(jsonPath("$.version").value(1));

    // A manager assigns to anyone in the workspace and reassigns and unassigns anyone's task.
    long forRep =
        id(
            send(
                    manager.token(),
                    post(TASKS, w),
                    Map.of("title", "For rep", "assigneeId", rep.userId()))
                .andExpect(jsonPath("$.assignee.userId").value(rep.userId())));
    send(manager.token(), put(TASK, w, own), edit("Renamed", other.userId(), 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee.userId").value(other.userId()));
    send(manager.token(), put(TASK, w, own), edit("Renamed", null, 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee").doesNotExist());
    send(manager.token(), put(TASK, w, forRep), edit("For rep", manager.userId(), 0))
        .andExpect(jsonPath("$.assignee.userId").value(manager.userId()));
    // Only a member can be assigned: a 400 on assigneeId, even for a manager.
    send(manager.token(), post(TASKS, w), Map.of("title", "X", "assigneeId", outsider.userId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.assigneeId").exists());
    send(manager.token(), put(TASK, w, own), edit("Renamed", outsider.userId(), 3))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.assigneeId").exists());
  }

  @Test
  void assigneeOrCreatorCanEdit() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    Member r1 = member(w, WorkspaceRole.SALES_REP);
    Member r2 = member(w, WorkspaceRole.SALES_REP);
    Member manager = member(w, WorkspaceRole.MANAGER);

    // Assigned to r1 by a manager: r1 is the assignee, not the creator.
    long assigned =
        id(
            send(
                manager.token(),
                post(TASKS, w),
                Map.of("title", "Assigned", "assigneeId", r1.userId())));
    send(r1.token(), put(TASK, w, assigned), edit("Assigned, edited", r1.userId(), 0))
        .andExpect(status().isOk());
    action(r1.token(), w, assigned, "complete", 1).andExpect(status().isOk());
    action(r1.token(), w, assigned, "reopen", 2).andExpect(status().isOk());
    action(r1.token(), w, assigned, "cancel", 3).andExpect(status().isOk());
    // Neither assignee nor creator: reading yes, everything else 403 (before any other check).
    send(r2.token(), get(TASK, w, assigned)).andExpect(status().isOk());
    send(r2.token(), put(TASK, w, assigned), edit("Taken", r1.userId(), 4))
        .andExpect(status().isForbidden());
    send(r2.token(), put(TASK, w, assigned), edit("Taken", r1.userId(), 0))
        .andExpect(status().isForbidden());
    for (String action : List.of("complete", "reopen", "cancel")) {
      action(r2.token(), w, assigned, action, 4).andExpect(status().isForbidden());
    }
    send(r2.token(), get(TASK, w, assigned))
        .andExpect(jsonPath("$.title").value("Assigned, edited"))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.version").value(4));

    // r1 created it, then a manager handed it to r2: the creator can still edit, and so can the
    // new assignee.
    long created = create(r1.token(), w, Map.of("title", "Created by r1"));
    send(manager.token(), put(TASK, w, created), edit("Created by r1", r2.userId(), 0))
        .andExpect(status().isOk());
    send(r1.token(), put(TASK, w, created), edit("By r1, edited", r2.userId(), 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee.userId").value(r2.userId()))
        .andExpect(jsonPath("$.createdBy.userId").value(r1.userId()));
    action(r1.token(), w, created, "complete", 2).andExpect(status().isOk());
    action(r2.token(), w, created, "reopen", 3).andExpect(status().isOk());
    send(r2.token(), put(TASK, w, created), edit("By r2, edited", r2.userId(), 4))
        .andExpect(status().isOk());

    // Unassigned: its creator and a manager can edit it, another rep can't.
    send(manager.token(), put(TASK, w, created), edit("By r2, edited", null, 5))
        .andExpect(jsonPath("$.assignee").doesNotExist());
    send(r1.token(), put(TASK, w, created), edit("Still r1's", null, 6)).andExpect(status().isOk());
    send(r2.token(), put(TASK, w, created), edit("Not r2's", null, 7))
        .andExpect(status().isForbidden());
    action(r2.token(), w, created, "complete", 7).andExpect(status().isForbidden());
  }

  @Test
  void assigneeAndCreatorBecomeEmptyOnceTheyLeave() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    Member creator = member(w, WorkspaceRole.SALES_REP);
    Member assignee = member(w, WorkspaceRole.SALES_REP);
    Member bystander = member(w, WorkspaceRole.SALES_REP);
    Member manager = member(w, WorkspaceRole.MANAGER);
    long id =
        id(
            send(
                manager.token(),
                post(TASKS, w),
                Map.of("title", "Handed over", "assigneeId", assignee.userId())));
    long byCreator = create(creator.token(), w, Map.of("title", "By the creator"));
    send(manager.token(), put(TASK, w, byCreator), edit("By the creator", manager.userId(), 0))
        .andExpect(status().isOk());

    leave(creator, w);
    leave(assignee, w);

    // The rows stay; the people are no longer shown, and the foreign keys cleared the columns.
    send(ownerToken, get(TASK, w, id))
        .andExpect(jsonPath("$.assignee").doesNotExist())
        .andExpect(jsonPath("$.createdBy.userId").value(manager.userId()));
    send(ownerToken, get(TASK, w, byCreator))
        .andExpect(jsonPath("$.createdBy").doesNotExist())
        .andExpect(jsonPath("$.assignee.userId").value(manager.userId()));
    // Nobody left to claim them: a rep can't edit, a manager can.
    send(bystander.token(), put(TASK, w, id), edit("Mine now", null, 0))
        .andExpect(status().isForbidden());
    send(bystander.token(), put(TASK, w, byCreator), edit("Mine now", manager.userId(), 1))
        .andExpect(status().isForbidden());
    action(bystander.token(), w, byCreator, "cancel", 1).andExpect(status().isForbidden());
    send(manager.token(), put(TASK, w, id), edit("Reassigned", bystander.userId(), 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee.userId").value(bystander.userId()));
    send(bystander.token(), put(TASK, w, id), edit("Reassigned, edited", bystander.userId(), 1))
        .andExpect(status().isOk());
  }

  @Test
  void completeReopenCancel() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
    long id = create(token, w, Map.of("title", "Send the invoice", "dueAt", yesterday.toString()));
    send(token, get(TASK, w, id)).andExpect(jsonPath("$.overdue").value(true));

    // Complete: completedAt set, no longer overdue, a new version.
    JsonNode completed =
        body(
            action(token, w, id, "complete", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.version").value(1)));
    // The answer equals a later read, completedAt included.
    assertThat(body(send(token, get(TASK, w, id)))).isEqualTo(completed);
    // No-ops and impossible moves are 409 and write nothing: complete again, cancel a completed
    // task, reopen an open one.
    action(token, w, id, "complete", 1).andExpect(status().isConflict());
    action(token, w, id, "cancel", 1).andExpect(status().isConflict());
    // A stale version is a 409 too.
    action(token, w, id, "reopen", 0).andExpect(status().isConflict());
    send(token, get(TASK, w, id)).andExpect(jsonPath("$.version").value(1));

    action(token, w, id, "reopen", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.completedAt").doesNotExist())
        .andExpect(jsonPath("$.overdue").value(true))
        .andExpect(jsonPath("$.version").value(2));
    action(token, w, id, "reopen", 2).andExpect(status().isConflict());

    action(token, w, id, "cancel", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.completedAt").doesNotExist())
        .andExpect(jsonPath("$.overdue").value(false))
        .andExpect(jsonPath("$.version").value(3));
    action(token, w, id, "cancel", 3).andExpect(status().isConflict());
    action(token, w, id, "complete", 3).andExpect(status().isConflict());
    // A cancelled task can be reopened, and then completed.
    action(token, w, id, "reopen", 3).andExpect(status().isOk());
    action(token, w, id, "complete", 4)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    // Editing doesn't change the status, and the same values are no change: no event.
    send(
            token,
            put(TASK, w, id),
            edit("Send the invoice", userId(email), 5, "dueAt", yesterday.toString()))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedAt").exists())
        .andExpect(jsonPath("$.version").value(5));

    // One event per change, newest first; the title is the label, the status and what follows it
    // are the changes. The no-ops and the stale attempts wrote nothing.
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            AuditAction.COMPLETE,
            AuditAction.REOPEN,
            AuditAction.CANCEL,
            AuditAction.REOPEN,
            AuditAction.COMPLETE,
            AuditAction.CREATE);
    assertThat(trail).allSatisfy(e -> assertThat(e.getEntityLabel()).isEqualTo("Send the invoice"));
    assertThat(trail.get(4).getChanges())
        .containsOnlyKeys("status", "completedAt", "overdue")
        .containsEntry("status", Map.of("from", "OPEN", "to", "COMPLETED"))
        .containsEntry("overdue", Map.of("from", true, "to", false));
    assertThat(trail.get(3).getChanges())
        .containsOnlyKeys("status", "completedAt", "overdue")
        .containsEntry("status", Map.of("from", "COMPLETED", "to", "OPEN"))
        .containsEntry("overdue", Map.of("from", false, "to", true));
    assertThat(trail.get(2).getChanges())
        .containsOnlyKeys("status", "overdue")
        .containsEntry("status", Map.of("from", "OPEN", "to", "CANCELLED"));
    assertThat(trail.get(5).getChanges()).isEmpty();
  }

  @Test
  void overdueFlagAndFilter() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
    long late = create(token, w, Map.of("title", "Late", "dueAt", yesterday.toString()));
    create(token, w, Map.of("title", "Soon", "dueAt", tomorrow.toString()));
    create(token, w, Map.of("title", "Whenever"));
    long done = create(token, w, Map.of("title", "Done", "dueAt", yesterday.toString()));
    long dropped = create(token, w, Map.of("title", "Dropped", "dueAt", yesterday.toString()));
    action(token, w, done, "complete", 0).andExpect(status().isOk());
    action(token, w, dropped, "cancel", 0).andExpect(status().isOk());

    send(token, get(TASK, w, late)).andExpect(jsonPath("$.overdue").value(true));
    send(token, get(TASK, w, done)).andExpect(jsonPath("$.overdue").value(false));
    send(token, get(TASK, w, dropped)).andExpect(jsonPath("$.overdue").value(false));
    // Overdue means OPEN with a due time in the past: the others are not, no due date included.
    list(token, w, "overdue", "true").andExpect(titles("Late"));
    list(token, w, "overdue", "false")
        .andExpect(titlesAnyOrder("Soon", "Whenever", "Done", "Dropped"));
    list(token, w).andExpect(jsonPath("$.totalElements").value(5));
    list(token, w, "overdue", "perhaps").andExpect(status().isBadRequest());
    list(token, w, "overdue", "true", "status", "COMPLETED")
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void sortByDueAtPutsNoDueDateLast() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("title", "none 1"));
    create(token, w, Map.of("title", "march", "dueAt", "2031-03-01T09:00:00Z"));
    create(token, w, Map.of("title", "none 2"));
    create(token, w, Map.of("title", "january", "dueAt", "2031-01-01T09:00:00Z"));
    create(token, w, Map.of("title", "february", "dueAt", "2031-02-01T09:00:00Z"));

    // Soonest first unless told, no due date last in both directions, the id breaking ties in the
    // direction of the sort.
    list(token, w).andExpect(titles("january", "february", "march", "none 1", "none 2"));
    list(token, w, "sort", "DUE_AT", "direction", "ASC")
        .andExpect(titles("january", "february", "march", "none 1", "none 2"));
    list(token, w, "sort", "DUE_AT", "direction", "DESC")
        .andExpect(titles("march", "february", "january", "none 2", "none 1"));
    list(token, w, "size", "2", "page", "1")
        .andExpect(titles("march", "none 1"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3));
    list(token, w, "size", "2", "page", "2", "direction", "DESC")
        .andExpect(titles("none 1"))
        .andExpect(jsonPath("$.totalElements").value(5));
    // Filters keep the order.
    list(token, w, "dueFrom", "2031-02-01T09:00:00Z").andExpect(titles("february", "march"));
  }

  @Test
  void sortByPriorityCreatedAtAndUpdatedAt() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long low = create(token, w, Map.of("title", "low", "priority", "LOW"));
    create(token, w, Map.of("title", "high 1", "priority", "HIGH"));
    create(token, w, Map.of("title", "medium"));
    create(token, w, Map.of("title", "high 2", "priority", "HIGH"));

    // Most urgent first unless told; ties by id in the direction of the sort.
    list(token, w, "sort", "PRIORITY").andExpect(titles("high 2", "high 1", "medium", "low"));
    list(token, w, "sort", "PRIORITY", "direction", "DESC")
        .andExpect(titles("high 2", "high 1", "medium", "low"));
    list(token, w, "sort", "PRIORITY", "direction", "ASC")
        .andExpect(titles("low", "medium", "high 1", "high 2"));
    list(token, w, "sort", "PRIORITY", "size", "3", "page", "1")
        .andExpect(titles("low"))
        .andExpect(jsonPath("$.totalElements").value(4));
    // Newest first unless told.
    list(token, w, "sort", "CREATED_AT").andExpect(titles("high 2", "medium", "high 1", "low"));
    list(token, w, "sort", "CREATED_AT", "direction", "ASC")
        .andExpect(titles("low", "high 1", "medium", "high 2"));
    // An edit makes a task the most recently updated.
    send(token, put(TASK, w, low), edit("low, edited", userId(email), 0, "priority", "LOW"))
        .andExpect(status().isOk());
    list(token, w, "sort", "UPDATED_AT")
        .andExpect(titles("low, edited", "high 2", "medium", "high 1"));
    list(token, w, "sort", "UPDATED_AT", "direction", "ASC")
        .andExpect(titles("high 1", "medium", "high 2", "low, edited"));

    list(token, w, "sort", "priority").andExpect(status().isBadRequest());
    list(token, w, "sort", "TITLE").andExpect(status().isBadRequest());
    list(token, w, "direction", "UP").andExpect(status().isBadRequest());
    list(token, w, "page", "-1").andExpect(status().isBadRequest());
    list(token, w, "size", "0").andExpect(status().isBadRequest());
    list(token, w, "size", "101").andExpect(status().isBadRequest());
  }

  @Test
  void filters() throws Exception {
    String ownerEmail = uniqueEmail();
    String token = tokenFor(ownerEmail);
    long w = createWorkspace(token, "Acme");
    Member rep = member(w, WorkspaceRole.SALES_REP);
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme")));
    long contact = id(send(token, post(CONTACTS, w), Map.of("firstName", "Hank")));
    long lead = id(send(token, post(LEADS, w), Map.of("firstName", "Ada")));
    long deal = id(send(token, post(DEALS, w), Map.of("name", "Big", "amount", "1")));

    create(
        token,
        w,
        Map.of(
            "title",
            "Alpha call",
            "dueAt",
            "2031-01-10T09:00:00Z",
            "companyId",
            acme,
            "contactId",
            contact));
    create(
        rep.token(),
        w,
        Map.of("title", "Beta mail", "dueAt", "2031-01-20T09:00:00Z", "leadId", lead));
    long gamma =
        create(
            token,
            w,
            Map.of(
                "title",
                "Gamma 100%",
                "dueAt",
                "2031-01-30T09:00:00Z",
                "dealId",
                deal,
                "assigneeId",
                rep.userId()));
    long delta = create(token, w, Map.of("title", "Delta_x"));
    create(token, w, Map.of("title", "Epsilon"));
    action(token, w, gamma, "complete", 0).andExpect(status().isOk());
    action(token, w, delta, "cancel", 0).andExpect(status().isOk());
    long ownerId = userId(ownerEmail);

    list(token, w, "status", "COMPLETED").andExpect(titles("Gamma 100%"));
    list(token, w, "status", "CANCELLED").andExpect(titles("Delta_x"));
    list(token, w, "status", "OPEN")
        .andExpect(titlesAnyOrder("Alpha call", "Beta mail", "Epsilon"));
    list(token, w, "status", "COMPLETED", "status", "CANCELLED")
        .andExpect(titlesAnyOrder("Gamma 100%", "Delta_x"));
    list(token, w, "status", "DONE").andExpect(status().isBadRequest());
    list(token, w, "assigneeId", String.valueOf(rep.userId()))
        .andExpect(titlesAnyOrder("Beta mail", "Gamma 100%"));
    list(token, w, "assigneeId", String.valueOf(ownerId))
        .andExpect(titlesAnyOrder("Alpha call", "Delta_x", "Epsilon"));
    list(rep.token(), w, "mine", "true").andExpect(titlesAnyOrder("Beta mail", "Gamma 100%"));
    list(token, w, "mine", "true").andExpect(titlesAnyOrder("Alpha call", "Delta_x", "Epsilon"));
    list(token, w, "mine", "false").andExpect(jsonPath("$.totalElements").value(5));
    // Mine and an assignee: both must hold.
    list(rep.token(), w, "mine", "true", "assigneeId", String.valueOf(ownerId))
        .andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "companyId", String.valueOf(acme)).andExpect(titles("Alpha call"));
    list(token, w, "contactId", String.valueOf(contact)).andExpect(titles("Alpha call"));
    list(token, w, "leadId", String.valueOf(lead)).andExpect(titles("Beta mail"));
    list(token, w, "dealId", String.valueOf(deal)).andExpect(titles("Gamma 100%"));
    // Instants, inclusive at both ends; tasks without a due date never match.
    list(token, w, "dueFrom", "2031-01-20T09:00:00Z").andExpect(titles("Beta mail", "Gamma 100%"));
    list(token, w, "dueTo", "2031-01-20T09:00:00Z").andExpect(titles("Alpha call", "Beta mail"));
    list(token, w, "dueFrom", "2031-01-20T09:00:00Z", "dueTo", "2031-01-20T09:00:00Z")
        .andExpect(titles("Beta mail"));
    list(token, w, "dueFrom", "2031-01-11T00:00:00Z", "dueTo", "2031-01-29T00:00:00Z")
        .andExpect(titles("Beta mail"));
    list(token, w, "dueFrom", "soon").andExpect(status().isBadRequest());
    list(token, w, "dueTo", "2031-01-20").andExpect(status().isBadRequest());
    // Title, any case, literally.
    list(token, w, "q", "ALPHA").andExpect(titles("Alpha call"));
    list(token, w, "q", "a ca").andExpect(titles("Alpha call"));
    list(token, w, "q", "%").andExpect(titles("Gamma 100%"));
    list(token, w, "q", "_").andExpect(titles("Delta_x"));
    list(token, w, "q", "zzz").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", " ").andExpect(jsonPath("$.totalElements").value(5));
    list(token, w, "q", "x".repeat(201)).andExpect(status().isBadRequest());
    list(token, w, "status", "OPEN", "q", "mail", "assigneeId", String.valueOf(rep.userId()))
        .andExpect(titles("Beta mail"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void linksOfAnotherWorkspaceAre400() throws Exception {
    String email = uniqueEmail();
    String a = tokenFor(email);
    long wa = createWorkspace(a, "A");
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    Member outsider = outsider();
    long theirCompany = id(send(b, post(COMPANIES, wb), Map.of("name", "B's")));
    long theirContact = id(send(b, post(CONTACTS, wb), Map.of("firstName", "B")));
    long theirLead = id(send(b, post(LEADS, wb), Map.of("firstName", "B")));
    long theirDeal = id(send(b, post(DEALS, wb), Map.of("name", "B's", "amount", "1")));
    long own = create(a, wa, Map.of("title", "Task"));
    Map<String, Long> theirs =
        Map.of(
            "companyId", theirCompany,
            "contactId", theirContact,
            "leadId", theirLead,
            "dealId", theirDeal);

    for (Map.Entry<String, Long> link : theirs.entrySet()) {
      send(a, post(TASKS, wa), Map.of("title", "X", link.getKey(), link.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.fieldErrors." + link.getKey()).value("is not found in this workspace"));
      send(a, put(TASK, wa, own), edit("Task", userId(email), 0, link.getKey(), link.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.fieldErrors." + link.getKey()).value("is not found in this workspace"));
      // An id that exists nowhere is the same 400.
      send(a, post(TASKS, wa), Map.of("title", "X", link.getKey(), Long.MAX_VALUE))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors." + link.getKey()).exists());
    }
    // A user who is no member of the workspace can't be assigned.
    send(a, post(TASKS, wa), Map.of("title", "X", "assigneeId", outsider.userId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.assigneeId").exists());
    send(a, put(TASK, wa, own), edit("Task", outsider.userId(), 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.assigneeId").exists());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(1));
    send(a, get(TASK, wa, own))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));

    // The composite foreign keys are the backstop, for the member columns too.
    Map<String, Long> columns =
        Map.of(
            "company_id", theirCompany,
            "contact_id", theirContact,
            "lead_id", theirLead,
            "deal_id", theirDeal,
            "assignee_id", userId(bEmail),
            "created_by", userId(bEmail));
    for (Map.Entry<String, Long> column : columns.entrySet()) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE tasks SET " + column.getKey() + " = ? WHERE id = ?",
                      column.getValue(),
                      own))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String email = uniqueEmail();
    String a = tokenFor(email);
    long wa = createWorkspace(a, "A");
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long theirs = create(a, wa, Map.of("title", "A's"));

    // B's token and workspace, A's task id.
    send(b, get(TASK, wb, theirs)).andExpect(status().isNotFound());
    send(b, put(TASK, wb, theirs), edit("Mine now", userId(bEmail), 0))
        .andExpect(status().isNotFound());
    for (String action : List.of("complete", "reopen", "cancel")) {
      action(b, wb, theirs, action, 0).andExpect(status().isNotFound());
    }
    list(b, wb).andExpect(jsonPath("$.totalElements").value(0));
    send(a, get(TASK, wa, theirs))
        .andExpect(jsonPath("$.title").value("A's"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.version").value(0));
    // B has no access to A's workspace at all.
    send(b, get(TASK, wa, theirs)).andExpect(status().isNotFound());
    list(b, wa).andExpect(status().isNotFound());
    send(a, get(TASK, wa, Long.MAX_VALUE)).andExpect(status().isNotFound());
    send(a, get(TASK, wb, theirs)).andExpect(status().isNotFound());
    assertThat(trail(wa, theirs)).hasSize(1);
  }

  /** Tasks are cancelled, never deleted. */
  @Test
  void thereIsNoDelete() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("title", "Keep me"));

    send(token, delete(TASK, w, id)).andExpect(status().isMethodNotAllowed());
    send(token, get(TASK, w, id)).andExpect(status().isOk());
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  /** A new member of the workspace with {@code role}. */
  private Member member(long w, WorkspaceRole role) throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    addMember(w, email, role);
    return new Member(email, token, userId(email));
  }

  /** A registered user who belongs to no workspace of the test. */
  private Member outsider() throws Exception {
    String email = uniqueEmail();
    return new Member(email, tokenFor(email), userId(email));
  }

  private void leave(Member member, long w) throws Exception {
    send(
            member.token(),
            delete("/api/v1/workspaces/{workspaceId}/members/{userId}", w, member.userId()))
        .andExpect(status().isNoContent());
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(TASKS, w), input));
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
  }

  /** A PUT body: title, assignee (null unassigns), version and any other fields as pairs. */
  private static Map<String, Object> edit(
      String title, Long assigneeId, long version, Object... more) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("assigneeId", assigneeId);
    body.put("version", version);
    for (int i = 0; i < more.length; i += 2) body.put((String) more[i], more[i + 1]);
    return body;
  }

  private ResultActions action(String token, long w, long id, String action, long version)
      throws Exception {
    return send(token, post(TASK + "/" + action, w, id), Map.of("version", version));
  }

  private void malformed(String token, long w, Map<String, ?> input) throws Exception {
    send(token, post(TASKS, w), input).andExpect(status().isBadRequest());
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(TASKS, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(TASKS, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  private static ResultMatcher titles(String... titles) {
    return jsonPath("$.content[*].title", contains(titles));
  }

  private static ResultMatcher titlesAnyOrder(String... titles) {
    return jsonPath("$.content[*].title", containsInAnyOrder(titles));
  }

  /** The task's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.TASK && e.getEntityId() == id)
        .toList();
  }
}
