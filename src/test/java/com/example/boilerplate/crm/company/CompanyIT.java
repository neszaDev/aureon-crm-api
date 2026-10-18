package com.example.boilerplate.crm.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CompanyIT extends WorkspaceTestSupport {
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String COMPANY = COMPANIES + "/{id}";

  @Autowired AuditEventRepository events;
  @Autowired CompanyRepository companies;

  @Test
  void createGetUpdate() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    Map<String, Object> input = new HashMap<>();
    input.put("name", " Globex　");
    input.put("industry", "Manufacturing");
    input.put("website", "https://globex.example");
    input.put("phone", "+1 555 0100");
    input.put("address", "1 Main Street, Springfield");
    input.put("employeeCount", 250);
    input.put("notes", "Key account");

    long id =
        body(send(token, post(COMPANIES, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Globex"))
                .andExpect(jsonPath("$.industry").value("Manufacturing"))
                .andExpect(jsonPath("$.website").value("https://globex.example"))
                .andExpect(jsonPath("$.phone").value("+1 555 0100"))
                .andExpect(jsonPath("$.address").value("1 Main Street, Springfield"))
                .andExpect(jsonPath("$.employeeCount").value(250))
                .andExpect(jsonPath("$.notes").value("Key account"))
                .andExpect(jsonPath("$.owner.userId").value(userId(email)))
                .andExpect(jsonPath("$.owner.email").value(email))
                .andExpect(jsonPath("$.contactCount").value(0))
                .andExpect(jsonPath("$.openDealCount").value(0))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();

    send(token, get(COMPANY, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.name").value("Globex"))
        .andExpect(jsonPath("$.address").value("1 Main Street, Springfield"))
        .andExpect(jsonPath("$.owner.email").value(email))
        .andExpect(jsonPath("$.version").value(0));

    // Omitted optional fields are cleared; the owner is kept by sending it back.
    Map<String, Object> edit =
        edit("Globex Corp", userId(email), 0, "industry", "Energy", "employeeCount", 300);
    send(token, put(COMPANY, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Globex Corp"))
        .andExpect(jsonPath("$.industry").value("Energy"))
        .andExpect(jsonPath("$.employeeCount").value(300))
        .andExpect(jsonPath("$.website").doesNotExist())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.address").doesNotExist())
        .andExpect(jsonPath("$.notes").doesNotExist())
        .andExpect(jsonPath("$.owner.email").value(email))
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(COMPANY, w, id), edit).andExpect(jsonPath("$.version").value(1));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(1).getEntityLabel()).isEqualTo("Globex");
    assertThat(trail.get(1).getChanges()).isEmpty();
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Globex Corp");
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys(
            "name", "industry", "website", "phone", "address", "employeeCount", "notes");
  }

  @Test
  void validation() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    invalid(token, w, Map.of(), "name");
    invalid(token, w, Map.of("name", " "), "name");
    invalid(token, w, Map.of("name", "　 "), "name");
    invalid(token, w, Map.of("name", "x".repeat(201)), "name");
    invalid(token, w, Map.of("name", "Acme", "website", "ftp://x"), "website");
    invalid(token, w, Map.of("name", "Acme", "website", "https://x y.example"), "website");
    invalid(token, w, Map.of("name", "Acme", "website", "acme.com"), "website");
    invalid(token, w, Map.of("name", "Acme", "employeeCount", -1), "employeeCount");
    invalid(token, w, Map.of("name", "Acme", "employeeCount", 10_000_001), "employeeCount");
    invalid(token, w, Map.of("name", "Acme", "notes", "x".repeat(5001)), "notes");
    long id = create(token, w, Map.of("name", "Acme", "employeeCount", 0));
    send(token, put(COMPANY, w, id), Map.of("name", "Acme"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());

    list(token, w).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void nullCharactersAre400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    // PostgreSQL refuses U+0000 in text; that is the client's mistake, not a conflict.
    send(token, post(COMPANIES, w), Map.of("name", "a\u0000b"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Text must not contain null characters"));
    // A query parameter is rejected before the database sees it: a field error, as for q.
    duplicates(token, w, "name", "a\u0000b")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.name").value("must not contain null characters"));
    list(token, w).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void searchFilterSortPage() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    String repEmail = uniqueEmail();
    tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long rep = userId(repEmail);
    create(token, w, Map.of("name", "beta", "industry", "software", "ownerId", rep));
    create(token, w, Map.of("name", "Alpha", "industry", "Software"));
    create(
        token,
        w,
        Map.of(
            "name", "Gamma",
            "industry", "Retail",
            "website", "https://gamma.example",
            "phone", "+66 2 123 4567"));

    list(token, w, "q", " ALP ").andExpect(names("Alpha"));
    list(token, w, "q", "GAMMA.EX").andExpect(names("Gamma"));
    list(token, w, "q", "2 123").andExpect(names("Gamma"));
    list(token, w, "q", "   ").andExpect(jsonPath("$.totalElements").value(3));

    // Names sort ignoring case, whatever the database collation does with "beta" vs "Gamma".
    list(token, w).andExpect(names("Alpha", "beta", "Gamma"));
    list(token, w, "sort", "NAME", "direction", "DESC").andExpect(names("Gamma", "beta", "Alpha"));
    list(token, w, "sort", "CREATED_AT", "direction", "ASC")
        .andExpect(names("beta", "Alpha", "Gamma"));
    // Without a direction a sort takes its natural one: the dates newest first.
    list(token, w, "sort", "CREATED_AT").andExpect(names("Gamma", "Alpha", "beta"));
    list(token, w, "sort", "UPDATED_AT").andExpect(names("Gamma", "Alpha", "beta"));

    list(token, w, "size", "2")
        .andExpect(names("Alpha", "beta"))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
    list(token, w, "size", "2", "page", "1").andExpect(names("Gamma"));

    list(token, w, "industry", "SOFTWARE").andExpect(names("Alpha", "beta"));
    list(token, w, "industry", "soft").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "ownerId", String.valueOf(rep)).andExpect(names("beta"));
    list(token, w, "ownerId", String.valueOf(rep), "industry", "software", "q", "BET")
        .andExpect(names("beta"))
        .andExpect(jsonPath("$.content[0].owner.email").value(repEmail));
  }

  @Test
  void searchTreatsWildcardsLiterally() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    for (String name : List.of("50% Off Ltd", "Fifty Off", "Under_score", "Underscore", "a\\b")) {
      create(token, w, Map.of("name", name));
    }

    list(token, w, "q", "%").andExpect(names("50% Off Ltd"));
    list(token, w, "q", "_").andExpect(names("Under_score"));
    list(token, w, "q", "a\\b").andExpect(names("a\\b"));
  }

  @Test
  void badSortIs400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    list(token, w, "sort", "FOUNDED").andExpect(status().isBadRequest());
    list(token, w, "sort", "name").andExpect(status().isBadRequest());
    list(token, w, "direction", "UP").andExpect(status().isBadRequest());
    list(token, w, "archived", "maybe").andExpect(status().isBadRequest());
    list(token, w, "ownerId", "me").andExpect(status().isBadRequest());
    list(token, w, "size", "101")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.size").exists());
    list(token, w, "page", "-1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.page").exists());
    list(token, w, "q", "x".repeat(201))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.q").exists());
  }

  @Test
  void archiveHidesAndRestoreReturns() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long a = create(token, w, Map.of("name", "A"));
    create(token, w, Map.of("name", "B"));

    send(token, post(COMPANY + "/archive", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.archivedAt").exists())
        .andExpect(jsonPath("$.version").value(1));
    // Archiving again changes nothing.
    String archivedAt = body(send(token, get(COMPANY, w, a))).get("archivedAt").asText();
    send(token, post(COMPANY + "/archive", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").value(archivedAt))
        .andExpect(jsonPath("$.version").value(1));

    list(token, w).andExpect(names("B"));
    list(token, w, "archived", "true").andExpect(names("A"));

    send(token, post(COMPANY + "/restore", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.archivedAt").doesNotExist())
        .andExpect(jsonPath("$.version").value(2));
    send(token, post(COMPANY + "/restore", w, a)).andExpect(jsonPath("$.version").value(2));

    list(token, w).andExpect(names("A", "B"));
    list(token, w, "archived", "true").andExpect(jsonPath("$.totalElements").value(0));
    assertThat(trail(w, a))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.RESTORE, AuditAction.ARCHIVE, AuditAction.CREATE);
  }

  @Test
  void duplicates() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long acme = create(token, w, Map.of("name", "ACME LTD", "website", "https://www.acme.com/x"));
    long other = create(token, w, Map.of("name", "Other", "website", "http://Acme.com"));
    create(token, w, Map.of("name", "Community", "website", "https://acme.community"));
    create(token, w, Map.of("name", "Shop", "website", "https://shop.acme.com"));
    long archived = create(token, w, Map.of("name", "acme ltd"));
    send(token, post(COMPANY + "/archive", w, archived)).andExpect(status().isOk());

    duplicates(token, w, "name", " acme ltd ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", contains((int) acme)))
        .andExpect(jsonPath("$[0].name").value("ACME LTD"))
        .andExpect(jsonPath("$[0].website").value("https://www.acme.com/x"));
    duplicates(token, w, "website", "http://acme.com")
        .andExpect(jsonPath("$[*].id", contains((int) acme, (int) other)));
    // Same name first, then same host; a company that is both is listed once.
    duplicates(token, w, "name", "Other", "website", "https://WWW.ACME.COM")
        .andExpect(jsonPath("$[*].id", contains((int) other, (int) acme)));
    duplicates(token, w, "name", "Nobody", "website", "https://nobody.example")
        .andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w, "website", "not a url").andExpect(jsonPath("$.length()").value(0));
    // No host (only "www.", or "www" alone) or a 1-2 character one matches nothing, not nearly
    // everything.
    long tiny = create(token, w, Map.of("name", "Tiny", "website", "http://ab"));
    create(token, w, Map.of("name", "Bare www", "website", "https://www"));
    duplicates(token, w, "website", "https://www.").andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w, "website", "https://www").andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w, "website", "https://ab").andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w, "name", "tiny", "website", "https://ab")
        .andExpect(jsonPath("$[*].id", contains((int) tiny)));
    duplicates(token, w).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

    for (int i = 0; i < 6; i++) create(token, w, Map.of("name", "Twin"));
    duplicates(token, w, "name", "twin").andExpect(jsonPath("$.length()").value(5));
  }

  @Test
  void duplicateCandidatesNeedTheHostRightAfterTheScheme() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("name", "Exact", "website", "https://acme.com"));
    create(token, w, Map.of("name", "Www", "website", "http://WWW.acme.com/about"));
    create(token, w, Map.of("name", "Longer", "website", "https://acme.community"));
    create(token, w, Map.of("name", "Inside", "website", "https://notacme.com"));
    create(token, w, Map.of("name", "Sub", "website", "https://shop.acme.com"));
    create(token, w, Map.of("name", "In path", "website", "https://example.org/acme.com"));
    Membership m = members.findMembership(w, email).orElseThrow();

    // The service then drops "Longer" by comparing hosts (see duplicates()).
    assertThat(
            WorkspaceContext.runAs(
                m,
                () ->
                    companies.findWebsiteCandidates("acme.com").stream()
                        .map(Company::getName)
                        .toList()))
        .containsExactly("Exact", "Www", "Longer");
  }

  @Test
  void websiteIsAnyCaseAndStripped() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    long id =
        body(send(
                    token,
                    post(COMPANIES, w),
                    Map.of("name", "Acme", "website", " HTTPS://x.example "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.website").value("HTTPS://x.example")))
            .get("id")
            .asLong();
    duplicates(token, w, "website", "https://X.example/")
        .andExpect(jsonPath("$[*].id", contains((int) id)));
  }

  @Test
  void salesRepEditsOnlyOwn() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    String outsiderEmail = uniqueEmail();
    tokenFor(outsiderEmail);
    long repId = userId(repEmail);
    long managerId = userId(managerEmail);

    long own =
        body(send(rep, post(COMPANIES, w), Map.of("name", "Rep's"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.userId").value(repId)))
            .get("id")
            .asLong();
    send(rep, put(COMPANY, w, own), edit("Rep's Ltd", repId, 0)).andExpect(status().isOk());

    long managers = create(manager, w, Map.of("name", "Manager's"));
    send(rep, get(COMPANY, w, managers)).andExpect(status().isOk());
    send(rep, put(COMPANY, w, managers), edit("Taken", repId, 0)).andExpect(status().isForbidden());
    send(rep, put(COMPANY, w, managers), edit("Taken", managerId, 0))
        .andExpect(status().isForbidden());
    send(rep, post(COMPANY + "/archive", w, managers)).andExpect(status().isForbidden());
    send(rep, post(COMPANY + "/restore", w, managers)).andExpect(status().isForbidden());

    // A rep can't hand a record to someone else, on create or on update.
    send(rep, post(COMPANIES, w), Map.of("name", "Gift", "ownerId", managerId))
        .andExpect(status().isForbidden());
    send(rep, put(COMPANY, w, own), edit("Rep's Ltd", managerId, 1))
        .andExpect(status().isForbidden());

    send(manager, put(COMPANY, w, own), edit("Rep's Ltd", managerId, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner.userId").value(managerId));
    send(manager, post(COMPANIES, w), Map.of("name", "For rep", "ownerId", repId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.owner.email").value(repEmail));
    long outsider = userId(outsiderEmail);
    send(manager, post(COMPANIES, w), Map.of("name", "Nope", "ownerId", outsider))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    send(manager, put(COMPANY, w, managers), edit("Manager's", outsider, 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());

    send(rep, post(COMPANY + "/archive", w, own)).andExpect(status().isForbidden());
    long another = create(rep, w, Map.of("name", "Rep's other"));
    send(rep, post(COMPANY + "/archive", w, another)).andExpect(status().isOk());
    send(rep, post(COMPANY + "/restore", w, another)).andExpect(status().isOk());
  }

  @Test
  void ownerOnUpdate() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    long repId = userId(repEmail);

    // A manager sending null unassigns.
    long id = create(ownerToken, w, Map.of("name", "Shared"));
    send(manager, put(COMPANY, w, id), edit("Shared", null, 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner").doesNotExist());
    // Editing an unassigned company with null keeps it unassigned: the editor doesn't take it.
    send(manager, put(COMPANY, w, id), edit("Shared Ltd", null, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Shared Ltd"))
        .andExpect(jsonPath("$.owner").doesNotExist());
    // Only RECORDS_EDIT_ANY edits an unowned record.
    send(rep, put(COMPANY, w, id), edit("Mine now", null, 2)).andExpect(status().isForbidden());
    send(rep, put(COMPANY, w, id), edit("Mine now", repId, 2)).andExpect(status().isForbidden());

    long own = create(rep, w, Map.of("name", "Rep's"));
    send(rep, put(COMPANY, w, own), edit("Rep's Ltd", repId, 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner.userId").value(repId));
    send(rep, put(COMPANY, w, own), edit("Rep's Ltd", null, 1)).andExpect(status().isForbidden());
    send(rep, get(COMPANY, w, own)).andExpect(jsonPath("$.owner.userId").value(repId));
  }

  @Test
  void removingAMemberLeavesTheirCompaniesUnassigned() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long id = create(rep, w, Map.of("name", "Rep's"));

    send(
            ownerToken,
            delete("/api/v1/workspaces/{workspaceId}/members/{userId}", w, userId(repEmail)))
        .andExpect(status().isNoContent());

    send(ownerToken, get(COMPANY, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Rep's"))
        .andExpect(jsonPath("$.owner").doesNotExist());
    list(ownerToken, w).andExpect(names("Rep's"));
  }

  @Test
  void staleVersionIs409() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("name", "Acme"));

    send(token, put(COMPANY, w, id), edit("First", userId(email), 0)).andExpect(status().isOk());
    send(token, put(COMPANY, w, id), edit("Second", userId(email), 0))
        .andExpect(status().isConflict());

    send(token, get(COMPANY, w, id)).andExpect(jsonPath("$.name").value("First"));
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long ofB = create(b, wb, Map.of("name", "B's"));

    send(a, get(COMPANY, wa, ofB)).andExpect(status().isNotFound());
    send(a, put(COMPANY, wa, ofB), edit("Hijacked", null, 0)).andExpect(status().isNotFound());
    send(a, post(COMPANY + "/archive", wa, ofB)).andExpect(status().isNotFound());
    send(a, post(COMPANY + "/restore", wa, ofB)).andExpect(status().isNotFound());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));
    duplicates(a, wa, "name", "B's").andExpect(jsonPath("$.length()").value(0));

    send(b, get(COMPANY, wb, ofB))
        .andExpect(jsonPath("$.name").value("B's"))
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.owner.email").value(bEmail))
        .andExpect(jsonPath("$.version").value(0));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return body(send(token, post(COMPANIES, w), input).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  /** A PUT body: name, owner (null unassigns), version and any other fields as pairs. */
  private static Map<String, Object> edit(String name, Long ownerId, long version, Object... more) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("ownerId", ownerId);
    body.put("version", version);
    for (int i = 0; i < more.length; i += 2) body.put((String) more[i], more[i + 1]);
    return body;
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(COMPANIES, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    return send(token, withParams(get(COMPANIES, w), params));
  }

  private ResultActions duplicates(String token, long w, String... params) throws Exception {
    return send(token, withParams(get(COMPANIES + "/duplicates", w), params));
  }

  private static MockHttpServletRequestBuilder withParams(
      MockHttpServletRequestBuilder request, String... params) {
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return request;
  }

  private static ResultMatcher names(String... names) {
    return jsonPath("$.content[*].name", contains(names));
  }

  /** The company's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.COMPANY && e.getEntityId() == id)
        .toList();
  }
}
