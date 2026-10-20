package com.example.boilerplate.crm.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ContactIT extends WorkspaceTestSupport {
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";
  static final String CONTACT = CONTACTS + "/{id}";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  @Test
  void createGetUpdate() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long acme = company(token, w, "Acme");
    JsonNode source = firstSource(token, w);
    Map<String, Object> input = new HashMap<>();
    input.put("firstName", " Ada　");
    input.put("lastName", "Lovelace");
    input.put("email", " Ada@Example.COM ");
    input.put("phone", "+44 20 7946 0000");
    input.put("jobTitle", "Engineer");
    input.put("companyId", acme);
    input.put("sourceId", source.get("id").asLong());
    input.put("tags", List.of("VIP"));
    input.put("notes", "Met at the expo");

    long id =
        body(send(token, post(CONTACTS, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phone").value("+44 20 7946 0000"))
                .andExpect(jsonPath("$.jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.company.id").value(acme))
                .andExpect(jsonPath("$.company.name").value("Acme"))
                .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
                .andExpect(jsonPath("$.source.name").value(source.get("name").asText()))
                .andExpect(jsonPath("$.tags", contains("vip")))
                .andExpect(jsonPath("$.notes").value("Met at the expo"))
                .andExpect(jsonPath("$.owner.userId").value(userId(email)))
                .andExpect(jsonPath("$.owner.email").value(email))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();

    send(token, get(CONTACT, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.company.name").value("Acme"))
        .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
        .andExpect(jsonPath("$.tags", contains("vip")))
        .andExpect(jsonPath("$.version").value(0));
    // The summary has the company but not the source.
    list(token, w)
        .andExpect(jsonPath("$.content[0].fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.content[0].company.name").value("Acme"))
        .andExpect(jsonPath("$.content[0].owner.email").value(email))
        .andExpect(jsonPath("$.content[0].tags", contains("vip")))
        .andExpect(jsonPath("$.content[0].source").doesNotExist());

    // Omitted optional fields are cleared: last name, company, source, tags, ...
    Map<String, Object> edit = edit("Ada", userId(email), 0, "email", "ada@lovelace.example");
    send(token, put(CONTACT, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ada"))
        .andExpect(jsonPath("$.lastName").doesNotExist())
        .andExpect(jsonPath("$.email").value("ada@lovelace.example"))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.tags").isEmpty())
        .andExpect(jsonPath("$.notes").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(CONTACT, w, id), edit).andExpect(jsonPath("$.version").value(1));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(1).getEntityLabel()).isEqualTo("Ada Lovelace");
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Ada");
    // fullName follows from the names: not a change of its own.
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys(
            "lastName", "email", "phone", "jobTitle", "company", "source", "tags", "notes");
  }

  @Test
  void validation() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    invalid(token, w, Map.of(), "firstName");
    invalid(token, w, Map.of("firstName", "　 "), "firstName");
    invalid(token, w, Map.of("firstName", "x".repeat(101)), "firstName");
    invalid(token, w, Map.of("firstName", "Ada", "lastName", "x".repeat(101)), "lastName");
    invalid(token, w, Map.of("firstName", "Ada", "email", "ada"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "email", "a b@example.com"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "email", "ada@example"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "phone", "1".repeat(51)), "phone");
    invalid(token, w, Map.of("firstName", "Ada", "jobTitle", "x".repeat(101)), "jobTitle");
    invalid(token, w, Map.of("firstName", "Ada", "notes", "x".repeat(5001)), "notes");
    invalid(token, w, Map.of("firstName", "Ada", "tags", List.of("ok", " ")), "tags");
    invalid(token, w, Map.of("firstName", "Ada", "tags", List.of("x".repeat(41))), "tags");
    invalid(token, w, Map.of("firstName", "Ada", "tags", Arrays.asList("ok", null)), "tags");
    List<String> many = IntStream.rangeClosed(1, 21).mapToObj(i -> "t" + i).toList();
    invalid(token, w, Map.of("firstName", "Ada", "tags", many), "tags");

    // 20 different tags are fine, and so is a duplicate of one of them.
    List<String> twenty = new ArrayList<>(many.subList(0, 20));
    twenty.add(" T1 ");
    long id = create(token, w, Map.of("firstName", "Ada", "tags", twenty));
    send(token, put(CONTACT, w, id), Map.of("firstName", "Ada"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());

    list(token, w)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].tags.length()").value(20));
  }

  @Test
  void companyFromAnotherWorkspaceIs400() throws Exception {
    String email = uniqueEmail();
    String a = tokenFor(email);
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirCompany = company(b, wb, "B's");
    long theirSource = firstSource(b, wb).get("id").asLong();

    send(a, post(CONTACTS, wa), Map.of("firstName", "Ada", "companyId", theirCompany))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.companyId").value("is not found in this workspace"));
    send(a, post(CONTACTS, wa), Map.of("firstName", "Ada", "sourceId", theirSource))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.sourceId").value("is not found in this workspace"));
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));

    long own = create(a, wa, Map.of("firstName", "Ada"));
    send(a, put(CONTACT, wa, own), edit("Ada", userId(email), 0, "companyId", theirCompany))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.companyId").exists());
    send(a, put(CONTACT, wa, own), edit("Ada", userId(email), 0, "sourceId", theirSource))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.sourceId").exists());
    send(a, get(CONTACT, wa, own))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void tagsAreNormalized() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(
            token,
            post(CONTACTS, w),
            Map.of("firstName", "Ada", "tags", List.of(" VIP ", "vip", "Partner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tags", contains("partner", "vip")));
  }

  /**
   * A tag is stored in Unicode's composed form (NFC), so "café" typed with a combining accent is
   * the same tag as with a precomposed é: one tag, one list entry, found by either spelling.
   * Composed after lower-casing: a capital W with a combining ring has no precomposed form, its
   * lower case has (ẘ).
   */
  @Test
  void tagsAreOneUnicodeForm() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    String composed = "caf\u00e9";
    String decomposed = "cafe\u0301";

    send(token, post(CONTACTS, w), Map.of("firstName", "Ada", "tags", List.of(decomposed)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tags", contains(composed)));
    create(token, w, Map.of("firstName", "Bob", "tags", List.of(composed, decomposed)));

    String ringW = "\u1e98";
    create(token, w, Map.of("firstName", "Cy", "tags", List.of("W\u030a")));
    create(token, w, Map.of("firstName", "Dee", "tags", List.of(ringW)));

    list(token, w, "tag", decomposed).andExpect(jsonPath("$.totalElements").value(2));
    list(token, w, "tag", composed).andExpect(jsonPath("$.totalElements").value(2));
    list(token, w, "tag", "W\u030a").andExpect(jsonPath("$.totalElements").value(2));
    send(token, get(CONTACTS + "/tags", w)).andExpect(jsonPath("$", contains(composed, ringW)));
  }

  @Test
  void tagFilterAndTagList() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(
        token, w, Map.of("firstName", "Ada", "lastName", "A", "tags", List.of("vip", "partner")));
    create(token, w, Map.of("firstName", "Bob", "lastName", "B", "tags", List.of("VIP")));
    create(token, w, Map.of("firstName", "Cy", "lastName", "C", "tags", List.of("lead")));
    long dee =
        create(token, w, Map.of("firstName", "Dee", "lastName", "D", "tags", List.of("old")));
    send(token, post(CONTACT + "/archive", w, dee)).andExpect(status().isOk());
    String other = tokenFor(uniqueEmail());
    long wo = createWorkspace(other, "Other");
    create(other, wo, Map.of("firstName", "Eve", "tags", List.of("secret", "vip")));

    list(token, w, "tag", "VIP ").andExpect(names("Ada A", "Bob B"));
    list(token, w, "tag", "partner").andExpect(names("Ada A"));
    list(token, w, "tag", "vi").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "tag", "old").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "tag", "old", "archived", "true").andExpect(names("Dee D"));

    // Archived contacts' tags and other workspaces' tags are not listed.
    send(token, get(CONTACTS + "/tags", w))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", contains("lead", "partner", "vip")));
    send(other, get(CONTACTS + "/tags", wo)).andExpect(jsonPath("$", contains("secret", "vip")));
  }

  @Test
  void searchByCompanyNameAndPhone() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long globex = company(token, w, "Globex Corp");
    create(
        token,
        w,
        Map.of(
            "firstName", "Ada",
            "lastName", "Lovelace",
            "phone", "+66 2 123 4567",
            "email", "ada@analytical.example",
            "companyId", globex));
    create(token, w, Map.of("firstName", "Bob", "lastName", "Builder", "email", "bob@b.example"));
    create(token, w, Map.of("firstName", "Cy"));

    list(token, w, "q", "GLOBEX").andExpect(names("Ada Lovelace"));
    list(token, w, "q", "2 123").andExpect(names("Ada Lovelace"));
    list(token, w, "q", " ada love ").andExpect(names("Ada Lovelace"));
    list(token, w, "q", "ANALYTICAL.").andExpect(names("Ada Lovelace"));
    // Contacts without a company are still searched by their own fields.
    list(token, w, "q", "build").andExpect(names("Bob Builder"));
    list(token, w, "q", "b.example").andExpect(names("Bob Builder"));
    list(token, w, "q", "cy").andExpect(names("Cy"));
    list(token, w, "q", "%").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", " ").andExpect(jsonPath("$.totalElements").value(3));
    list(token, w, "companyId", String.valueOf(globex)).andExpect(names("Ada Lovelace"));
  }

  @Test
  void sortByName() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("firstName", "Bob", "lastName", "smith"));
    create(token, w, Map.of("firstName", "alice", "lastName", "Smith"));
    create(token, w, Map.of("firstName", "Zed"));
    create(token, w, Map.of("firstName", "Carl", "lastName", "Adams"));

    // Last name, then first name, ignoring case; no last name: last ascending, first descending.
    list(token, w).andExpect(names("Carl Adams", "alice Smith", "Bob smith", "Zed"));
    list(token, w, "direction", "DESC")
        .andExpect(names("Zed", "Bob smith", "alice Smith", "Carl Adams"));
    list(token, w, "size", "3", "page", "1")
        .andExpect(names("Zed"))
        .andExpect(jsonPath("$.totalElements").value(4));
    list(token, w, "sort", "CREATED_AT", "direction", "ASC")
        .andExpect(names("Bob smith", "alice Smith", "Zed", "Carl Adams"));
    list(token, w, "sort", "UPDATED_AT", "direction", "DESC")
        .andExpect(names("Carl Adams", "Zed", "alice Smith", "Bob smith"));
    // Without a direction a sort takes its natural one: the dates newest first.
    list(token, w, "sort", "CREATED_AT")
        .andExpect(names("Carl Adams", "Zed", "alice Smith", "Bob smith"));
    list(token, w, "sort", "UPDATED_AT")
        .andExpect(names("Carl Adams", "Zed", "alice Smith", "Bob smith"));
    list(token, w, "sort", "name").andExpect(status().isBadRequest());
  }

  @Test
  void sortByCompany() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long gamma = company(token, w, "Gamma");
    long beta = company(token, w, "beta Inc");
    long alpha = company(token, w, "Alpha");
    create(token, w, Map.of("firstName", "G", "companyId", gamma));
    create(token, w, Map.of("firstName", "B", "companyId", beta));
    create(token, w, Map.of("firstName", "None"));
    create(token, w, Map.of("firstName", "A1", "companyId", alpha));
    create(token, w, Map.of("firstName", "A2", "companyId", alpha));

    // Company names ignoring case, then the id; no company: last ascending, first descending.
    list(token, w, "sort", "COMPANY").andExpect(names("A1", "A2", "B", "G", "None"));
    list(token, w, "sort", "COMPANY", "direction", "DESC")
        .andExpect(names("None", "G", "B", "A2", "A1"));
    list(token, w, "sort", "COMPANY", "size", "2")
        .andExpect(names("A1", "A2"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3));
    list(token, w, "sort", "COMPANY", "size", "2", "page", "1")
        .andExpect(names("B", "G"))
        .andExpect(jsonPath("$.totalElements").value(5));
    list(token, w, "sort", "COMPANY", "size", "2", "page", "2").andExpect(names("None"));
    // With a search on the company name too, the join and the count still agree.
    list(token, w, "sort", "COMPANY", "q", "a", "size", "2")
        .andExpect(names("A1", "A2"))
        .andExpect(jsonPath("$.totalElements").value(4));
  }

  @Test
  void duplicatesByEmailAndPhone() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long ada =
        create(
            token,
            w,
            Map.of(
                "firstName", "Ada",
                "lastName", "Lovelace",
                "email", "ada@example.com",
                "phone", "+66 (2) 123-4567"));
    long bob = create(token, w, Map.of("firstName", "Bob", "email", "BOB@example.com"));
    long cy = create(token, w, Map.of("firstName", "Cy", "phone", "66 2 123 4567"));
    create(token, w, Map.of("firstName", "Short", "phone", "1-2345"));
    long archived = create(token, w, Map.of("firstName", "Old", "email", "ada@example.com"));
    send(token, post(CONTACT + "/archive", w, archived)).andExpect(status().isOk());

    duplicates(token, w, "email", " ADA@example.COM ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", contains((int) ada)))
        .andExpect(jsonPath("$[0].fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$[0].email").value("ada@example.com"))
        .andExpect(jsonPath("$[0].phone").value("+66 (2) 123-4567"));
    duplicates(token, w, "phone", "+66-2-123-4567")
        .andExpect(jsonPath("$[*].id", contains((int) ada, (int) cy)));
    // Same email first, then same phone digits; a contact that is both is listed once.
    duplicates(token, w, "email", "bob@example.com", "phone", "6621234567")
        .andExpect(jsonPath("$[*].id", contains((int) bob, (int) ada, (int) cy)));
    duplicates(token, w, "email", "ada@example.com", "phone", "662 123 4567")
        .andExpect(jsonPath("$[*].id", contains((int) ada, (int) cy)));
    // Fewer than 6 digits match nothing.
    duplicates(token, w, "phone", "12345").andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w, "phone", "no digits").andExpect(jsonPath("$.length()").value(0));
    duplicates(token, w).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

    for (int i = 0; i < 6; i++) create(token, w, Map.of("firstName", "Twin", "phone", "555 0101"));
    duplicates(token, w, "phone", "5550101").andExpect(jsonPath("$.length()").value(5));

    String other = tokenFor(uniqueEmail());
    long wo = createWorkspace(other, "Other");
    duplicates(other, wo, "email", "ada@example.com", "phone", "+66 2 123 4567")
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void companyContactCountCountsOnlyActive() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long acme = company(token, w, "Acme");
    company(token, w, "Bare");
    long first = create(token, w, Map.of("firstName", "One", "companyId", acme));
    create(token, w, Map.of("firstName", "Two", "companyId", acme));
    create(token, w, Map.of("firstName", "Loose"));

    send(token, get(COMPANIES + "/{id}", w, acme)).andExpect(jsonPath("$.contactCount").value(2));
    send(token, get(COMPANIES, w))
        .andExpect(jsonPath("$.content[*].name", contains("Acme", "Bare")))
        .andExpect(jsonPath("$.content[*].contactCount", contains(2, 0)));

    send(token, post(CONTACT + "/archive", w, first)).andExpect(status().isOk());
    send(token, get(COMPANIES + "/{id}", w, acme)).andExpect(jsonPath("$.contactCount").value(1));
    send(token, get(COMPANIES, w)).andExpect(jsonPath("$.content[*].contactCount", contains(1, 0)));
  }

  @Test
  void archiveHidesAndRestoreReturns() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long a = create(token, w, Map.of("firstName", "A"));
    create(token, w, Map.of("firstName", "B"));

    send(token, post(CONTACT + "/archive", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.archivedAt").exists())
        .andExpect(jsonPath("$.version").value(1));
    send(token, post(CONTACT + "/archive", w, a)).andExpect(jsonPath("$.version").value(1));
    list(token, w).andExpect(names("B"));
    list(token, w, "archived", "true").andExpect(names("A"));

    send(token, post(CONTACT + "/restore", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.version").value(2));
    send(token, post(CONTACT + "/restore", w, a)).andExpect(jsonPath("$.version").value(2));
    list(token, w).andExpect(names("A", "B"));
    assertThat(trail(w, a))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.RESTORE, AuditAction.ARCHIVE, AuditAction.CREATE);
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
        body(send(rep, post(CONTACTS, w), Map.of("firstName", "Rep's"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.userId").value(repId)))
            .get("id")
            .asLong();
    send(rep, put(CONTACT, w, own), edit("Rep's own", repId, 0)).andExpect(status().isOk());

    long managers = create(manager, w, Map.of("firstName", "Manager's"));
    send(rep, get(CONTACT, w, managers)).andExpect(status().isOk());
    send(rep, put(CONTACT, w, managers), edit("Taken", repId, 0)).andExpect(status().isForbidden());
    send(rep, put(CONTACT, w, managers), edit("Taken", managerId, 0))
        .andExpect(status().isForbidden());
    send(rep, post(CONTACT + "/archive", w, managers)).andExpect(status().isForbidden());
    send(rep, post(CONTACT + "/restore", w, managers)).andExpect(status().isForbidden());
    send(rep, post(CONTACTS, w), Map.of("firstName", "Gift", "ownerId", managerId))
        .andExpect(status().isForbidden());
    send(rep, put(CONTACT, w, own), edit("Rep's own", null, 1)).andExpect(status().isForbidden());

    send(manager, put(CONTACT, w, own), edit("Rep's own", managerId, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner.userId").value(managerId));
    send(manager, post(CONTACTS, w), Map.of("firstName", "For rep", "ownerId", repId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.owner.email").value(repEmail));
    send(manager, post(CONTACTS, w), Map.of("firstName", "Nope", "ownerId", userId(outsiderEmail)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    // Now the manager's, so the rep can't archive it any more.
    send(rep, post(CONTACT + "/archive", w, own)).andExpect(status().isForbidden());
  }

  @Test
  void staleVersionIs409() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));

    send(token, put(CONTACT, w, id), edit("First", userId(email), 0)).andExpect(status().isOk());
    send(token, put(CONTACT, w, id), edit("Second", userId(email), 0))
        .andExpect(status().isConflict());

    send(token, get(CONTACT, w, id)).andExpect(jsonPath("$.firstName").value("First"));
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long ofB = create(b, wb, Map.of("firstName", "B's", "tags", List.of("theirs")));

    send(a, get(CONTACT, wa, ofB)).andExpect(status().isNotFound());
    send(a, put(CONTACT, wa, ofB), edit("Hijacked", null, 0)).andExpect(status().isNotFound());
    send(a, post(CONTACT + "/archive", wa, ofB)).andExpect(status().isNotFound());
    send(a, post(CONTACT + "/restore", wa, ofB)).andExpect(status().isNotFound());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));
    send(a, get(CONTACTS + "/tags", wa)).andExpect(jsonPath("$.length()").value(0));

    send(b, get(CONTACT, wb, ofB))
        .andExpect(jsonPath("$.firstName").value("B's"))
        .andExpect(jsonPath("$.version").value(0));
  }

  /** The service checks the links (400); the composite foreign keys are the backstop. */
  @Test
  void linksToAnotherWorkspaceFailInTheDatabase() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long own = create(a, wa, Map.of("firstName", "Ada"));
    Map<String, Long> theirs =
        Map.of(
            "company_id", company(b, wb, "B's"),
            "source_id", firstSource(b, wb).get("id").asLong(),
            "owner_id", userId(bEmail));

    theirs.forEach(
        (column, value) ->
            assertThatThrownBy(
                    () ->
                        jdbc.update(
                            "UPDATE contacts SET " + column + " = ? WHERE id = ?", value, own))
                .as(column)
                .isInstanceOf(DataIntegrityViolationException.class));
    send(a, get(CONTACT, wa, own))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void removingAMemberLeavesTheirContactsUnassigned() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long id = create(rep, w, Map.of("firstName", "Rep's"));

    send(
            ownerToken,
            delete("/api/v1/workspaces/{workspaceId}/members/{userId}", w, userId(repEmail)))
        .andExpect(status().isNoContent());

    send(ownerToken, get(CONTACT, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Rep's"))
        .andExpect(jsonPath("$.owner").doesNotExist());
    list(ownerToken, w).andExpect(jsonPath("$.content[*].id", contains((int) id)));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return body(send(token, post(CONTACTS, w), input).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private long company(String token, long w, String name) throws Exception {
    return body(send(token, post(COMPANIES, w), Map.of("name", name))
            .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  /** One of the lead sources every workspace starts with. */
  private JsonNode firstSource(String token, long w) throws Exception {
    return body(send(token, get("/api/v1/workspaces/{workspaceId}/lead-sources", w))).get(0);
  }

  /** A PUT body: first name, owner (null unassigns), version and any other fields as pairs. */
  private static Map<String, Object> edit(
      String firstName, Long ownerId, long version, Object... more) {
    Map<String, Object> body = new HashMap<>();
    body.put("firstName", firstName);
    body.put("ownerId", ownerId);
    body.put("version", version);
    for (int i = 0; i < more.length; i += 2) body.put((String) more[i], more[i + 1]);
    return body;
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(CONTACTS, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    return send(token, withParams(get(CONTACTS, w), params));
  }

  private ResultActions duplicates(String token, long w, String... params) throws Exception {
    return send(token, withParams(get(CONTACTS + "/duplicates", w), params));
  }

  private static MockHttpServletRequestBuilder withParams(
      MockHttpServletRequestBuilder request, String... params) {
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return request;
  }

  private static ResultMatcher names(String... fullNames) {
    return jsonPath("$.content[*].fullName", contains(fullNames));
  }

  /** The contact's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.CONTACT && e.getEntityId() == id)
        .toList();
  }
}
