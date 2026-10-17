package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class WorkspaceIT extends WorkspaceTestSupport {
  @Autowired EntityManagerFactory emf;

  @Test
  void createMakesCallerOwner() throws Exception {
    String token = tokenFor(uniqueEmail());

    String body =
        create(token, workspace("Acme", "EUR", "Europe/Paris"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme"))
            .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
            .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.permissions", hasItem("MEMBERS_MANAGE")))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(body).get("id").asLong();

    mvc.perform(get("/api/v1/workspaces").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].name").value("Acme"))
        .andExpect(jsonPath("$[0].role").value("OWNER"));
    read(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void listIsSortedByNameIgnoringCase() throws Exception {
    String token = tokenFor(uniqueEmail());
    for (String name : List.of("bravo", "Charlie", "Acme")) createWorkspace(token, name);

    mvc.perform(get("/api/v1/workspaces").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", contains("Acme", "bravo", "Charlie")));
  }

  @Test
  void invalidCurrencyAndTimezoneAre400() throws Exception {
    String token = tokenFor(uniqueEmail());

    create(token, workspace("Acme", "XYZ", "Europe/Paris"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.defaultCurrency").exists());
    create(token, workspace("Acme", "EUR", "Mars/Base"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.timezone").exists());
    create(token, workspace("Acme", "eur", "Europe/Paris"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.defaultCurrency").exists());

    long id = createWorkspace(token, "Acme");
    update(token, id, workspace("Acme", "XYZ", "Europe/Paris"), 0L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.defaultCurrency").exists());
    update(token, id, workspace("Acme", "EUR", "Mars/Base"), 0L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.timezone").exists());
  }

  @Test
  void nonMemberGets404() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long id = createWorkspace(owner, "Private");
    String other = tokenFor(uniqueEmail());

    read(other, id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Workspace not found"));
    update(other, id, workspace("Hijacked", "EUR", "Europe/Paris"), 0L)
        .andExpect(status().isNotFound());
    // A system admin manages accounts, not workspaces it isn't a member of.
    read(adminAccessToken(), id).andExpect(status().isNotFound());
    read(owner, 999_999_999L).andExpect(status().isNotFound());
    for (String bad : List.of("abc", "0", "-1", "99999999999999999999", "0x10")) {
      mvc.perform(get("/api/v1/workspaces/" + bad).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
          .andExpect(status().isNotFound());
    }
    mvc.perform(
            get("/api/v1/workspaces/{id}/no-such-endpoint", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isNotFound());

    read(owner, id).andExpect(jsonPath("$.name").value("Private"));
  }

  @Test
  void managerCannotUpdateWorkspace() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long id = createWorkspace(owner, "Acme");
    String managerEmail = uniqueEmail();
    String adminEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    String admin = tokenFor(adminEmail);
    addMember(id, managerEmail, WorkspaceRole.MANAGER);
    addMember(id, adminEmail, WorkspaceRole.ADMIN);

    read(manager, id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(
            jsonPath(
                "$.permissions",
                containsInAnyOrder("RECORDS_EDIT_ANY", "RECORDS_ASSIGN", "EXPORT")));
    update(manager, id, workspace("Renamed", "USD", "America/New_York"), 0L)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Your role can't do this"));

    update(admin, id, workspace("Renamed", "USD", "America/New_York"), 0L)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.defaultCurrency").value("USD"))
        .andExpect(jsonPath("$.timezone").value("America/New_York"))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void staleVersionIs409() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long id = createWorkspace(owner, "Acme");

    update(owner, id, workspace("First", "EUR", "Europe/Paris"), 0L)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    update(owner, id, workspace("Second", "EUR", "Europe/Paris"), 0L)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Changed by another request; reload and try again"));
    // The interceptor clears the context even when the handler throws (MockMvc runs on this
    // thread).
    assertThat(WorkspaceContext.current()).isEmpty();
    update(owner, id, workspace("Second", "EUR", "Europe/Paris"), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());

    read(owner, id).andExpect(jsonPath("$.name").value("First"));
  }

  @Test
  void roleChangeAppliesImmediately() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long id = createWorkspace(owner, "Acme");
    String memberEmail = uniqueEmail();
    String member = tokenFor(memberEmail);
    addMember(id, memberEmail, WorkspaceRole.MANAGER);
    read(member, id).andExpect(status().isOk());

    long userId = users.findByEmail(memberEmail).orElseThrow().getId();
    members.delete(members.findByWorkspaceIdAndUserId(id, userId).orElseThrow());

    read(member, id).andExpect(status().isNotFound());
  }

  /** The resolver is wired into Hibernate; with no context a session's tenant matches nothing. */
  @Test
  void sessionsTakeTheCurrentWorkspaceAsTenant() {
    assertThat(tenantOfNewSession()).isEqualTo(WorkspaceTenantResolver.NONE);
    Membership m = new Membership(42, 1, "someone@example.com", WorkspaceRole.SALES_REP);
    assertThat(WorkspaceContext.runAs(m, this::tenantOfNewSession)).isEqualTo(42L);
  }

  private Object tenantOfNewSession() {
    try (Session s = emf.unwrap(SessionFactory.class).openSession()) {
      return s.getTenantIdentifierValue();
    }
  }

  private Map<String, Object> workspace(String name, String currency, String timezone) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("defaultCurrency", currency);
    body.put("timezone", timezone);
    return body;
  }

  private ResultActions create(String token, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post("/api/v1/workspaces")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions read(String token, long id) throws Exception {
    return mvc.perform(
        get("/api/v1/workspaces/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)));
  }

  private ResultActions update(String token, long id, Map<String, Object> body, Long version)
      throws Exception {
    body.put("version", version);
    return mvc.perform(
        put("/api/v1/workspaces/{id}", id)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }
}
