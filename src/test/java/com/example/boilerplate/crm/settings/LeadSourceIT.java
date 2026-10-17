package com.example.boilerplate.crm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LeadSourceIT extends WorkspaceTestSupport {
  static final String SOURCES = "/api/v1/workspaces/{workspaceId}/lead-sources";
  static final String SOURCE = SOURCES + "/{id}";

  @Autowired AuditEventRepository events;

  @Test
  void newWorkspaceHasDefaultSources() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, get(SOURCES, w))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].name",
                contains("Event", "Other", "Outbound", "Partner", "Referral", "Website")))
        .andExpect(jsonPath("$[*].archived", everyItem(is(false))))
        .andExpect(jsonPath("$[*].version", everyItem(is(0))));
  }

  @Test
  void createAndDuplicateNameIs409() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, post(SOURCES, w), Map.of("name", "Trade show"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Trade show"))
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.version").value(0));
    send(token, post(SOURCES, w), Map.of("name", "website")).andExpect(status().isConflict());
    send(token, post(SOURCES, w), Map.of("name", " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists());

    send(token, get(SOURCES, w)).andExpect(jsonPath("$[*].name", hasItem("Trade show")));
  }

  @Test
  void nullCharacterInNameIs400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, post(SOURCES, w), Map.of("name", "Trade\u0000show"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Text must not contain null characters"));
    send(token, get(SOURCES, w)).andExpect(jsonPath("$.length()").value(6));
  }

  @Test
  void archiveHidesFromDefaultList() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = idOf(token, w, "Website");

    send(token, put(SOURCE, w, id), Map.of("name", "Website", "archived", true, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.version").value(1));

    send(token, get(SOURCES, w)).andExpect(jsonPath("$[*].name", not(hasItem("Website"))));
    send(token, get(SOURCES, w).param("includeArchived", "true"))
        .andExpect(jsonPath("$[?(@.name == 'Website')].archived", contains(true)));

    send(token, put(SOURCE, w, id), Map.of("name", "Website", "archived", false, "version", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false));
    send(token, get(SOURCES, w)).andExpect(jsonPath("$[*].name", hasItem("Website")));
  }

  @Test
  void renameAndStaleVersionIs409() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = idOf(token, w, "Website");

    send(token, put(SOURCE, w, id), Map.of("name", "Web", "archived", false, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Web"));
    send(token, put(SOURCE, w, id), Map.of("name", "Site", "archived", false, "version", 0))
        .andExpect(status().isConflict());
    send(token, put(SOURCE, w, id), Map.of("name", "REFERRAL", "archived", false, "version", 1))
        .andExpect(status().isConflict());

    assertThat(idOf(token, w, "Web")).isEqualTo(id);
  }

  @Test
  void omittedArchivedIs400NotFalse() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = idOf(token, w, "Website");

    send(token, put(SOURCE, w, id), Map.of("name", "Web", "version", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.archived").exists());
  }

  @Test
  void deleteUnusedIs204() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = body(send(token, post(SOURCES, w), Map.of("name", "Trade show"))).get("id").asLong();
    send(token, put(SOURCE, w, id), Map.of("name", "Fair", "archived", false, "version", 0))
        .andExpect(status().isOk());

    send(token, delete(SOURCE, w, id)).andExpect(status().isNoContent());

    send(token, get(SOURCES, w).param("includeArchived", "true"))
        .andExpect(jsonPath("$[*].id", not(hasItem((int) id))))
        .andExpect(jsonPath("$.length()").value(6));
    send(token, delete(SOURCE, w, id)).andExpect(status().isNotFound());
    assertThat(events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w))
        .filteredOn(e -> e.getEntityType() == AuditEntityType.LEAD_SOURCE)
        .filteredOn(e -> e.getEntityId() == id)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.DELETE, AuditAction.UPDATE, AuditAction.CREATE);
  }

  @Test
  void salesRepCanReadButNotWrite() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long id = idOf(owner, w, "Website");

    send(rep, get(SOURCES, w)).andExpect(status().isOk());
    send(rep, post(SOURCES, w), Map.of("name", "Trade show")).andExpect(status().isForbidden());
    send(rep, put(SOURCE, w, id), Map.of("name", "Web", "archived", false, "version", 0))
        .andExpect(status().isForbidden());
    send(rep, delete(SOURCE, w, id)).andExpect(status().isForbidden());
  }

  private long idOf(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(SOURCES, w).param("includeArchived", "true")))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No source " + name);
  }
}
