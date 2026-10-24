package com.example.boilerplate.crm.common;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every list with an owner filter (tasks: assignee), and the deal board, can ask for the records
 * that have none.
 */
class UnassignedFilterIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";

  /** A list, the field that names its records, its owner parameter and what a create needs. */
  record Listing(String path, String nameField, String ownerParam, Map<String, Object> more) {}

  static final List<Listing> LISTINGS =
      List.of(
          new Listing("/companies", "name", "ownerId", Map.of()),
          new Listing("/contacts", "firstName", "ownerId", Map.of()),
          new Listing("/leads", "firstName", "ownerId", Map.of()),
          new Listing("/deals", "name", "ownerId", Map.of("amount", "1")),
          new Listing("/tasks", "title", "assigneeId", Map.of()));

  @Test
  void unassignedListsTheRecordsWithoutAnOwner() throws Exception {
    String ownerEmail = uniqueEmail();
    String owner = tokenFor(ownerEmail);
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    for (Listing l : LISTINGS) {
      create(owner, w, l, "Kept");
      create(rep, w, l, "Left");
    }
    // Removing the member leaves their records unassigned.
    send(owner, delete(W + "/members/{userId}", w, userId(repEmail)))
        .andExpect(status().isNoContent());

    for (Listing l : LISTINGS) {
      send(owner, get(W + l.path(), w).param("unassigned", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[*]." + l.nameField(), contains("Left")));
      send(owner, get(W + l.path(), w).param("unassigned", "false"))
          .andExpect(jsonPath("$.totalElements").value(2));
      // Without an owner and with one can't both hold: the client's mistake.
      send(
              owner,
              get(W + l.path(), w)
                  .param("unassigned", "true")
                  .param(l.ownerParam(), String.valueOf(userId(ownerEmail))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors.unassigned").exists());
    }
    // The deal board filters as the deal list does; its counts follow.
    String board = W + "/deals/board";
    send(owner, get(board, w).param("unassigned", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stages[*].deals[*].name", contains("Left")))
        .andExpect(jsonPath("$.stages[0].count").value(1));
    send(owner, get(board, w).param("unassigned", "false"))
        .andExpect(jsonPath("$.stages[*].deals[*].name", containsInAnyOrder("Kept", "Left")))
        .andExpect(jsonPath("$.stages[0].count").value(2));
    send(
            owner,
            get(board, w)
                .param("unassigned", "true")
                .param("ownerId", String.valueOf(userId(ownerEmail))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.unassigned").exists());
    // The caller's own tasks have an assignee too.
    send(owner, get(W + "/tasks", w).param("unassigned", "true").param("mine", "true"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.unassigned").exists());
  }

  private void create(String token, long w, Listing l, String name) throws Exception {
    Map<String, Object> body = new HashMap<>(l.more());
    body.put(l.nameField(), name);
    send(token, post(W + l.path(), w), body).andExpect(status().isCreated());
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }
}
