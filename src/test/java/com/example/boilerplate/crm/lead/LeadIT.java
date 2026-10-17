package com.example.boilerplate.crm.lead;

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

class LeadIT extends WorkspaceTestSupport {
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";
  static final String LEAD = LEADS + "/{id}";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  @Test
  void createGetUpdate() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    JsonNode source = firstSource(token, w);
    Map<String, Object> input = new HashMap<>();
    input.put("firstName", " Ada　");
    input.put("lastName", "Lovelace");
    input.put("companyName", " Analytical Engines ");
    input.put("email", " Ada@Example.COM ");
    input.put("phone", "+44 20 7946 0000");
    input.put("jobTitle", "Engineer");
    input.put("sourceId", source.get("id").asLong());
    input.put("priority", "HIGH");
    input.put("followUpDate", "2026-11-03");
    input.put("estimatedValue", "1250");
    input.put("currency", "usd");
    input.put("notes", "Met at the expo");

    long id =
        body(send(token, post(LEADS, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.companyName").value("Analytical Engines"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phone").value("+44 20 7946 0000"))
                .andExpect(jsonPath("$.jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
                .andExpect(jsonPath("$.source.name").value(source.get("name").asText()))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.followUpDate").value("2026-11-03"))
                .andExpect(jsonPath("$.estimatedValue").value("1250.00"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.notes").value("Met at the expo"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.unqualifiedReason").doesNotExist())
                .andExpect(jsonPath("$.convertedAt").doesNotExist())
                .andExpect(jsonPath("$.convertedCompany").doesNotExist())
                .andExpect(jsonPath("$.convertedContact").doesNotExist())
                .andExpect(jsonPath("$.convertedDeal").doesNotExist())
                .andExpect(jsonPath("$.owner.userId").value(userId(email)))
                .andExpect(jsonPath("$.owner.email").value(email))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();

    send(token, get(LEAD, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.estimatedValue").value("1250.00"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
        .andExpect(jsonPath("$.version").value(0));
    list(token, w)
        .andExpect(jsonPath("$.content[0].firstName").value("Ada"))
        .andExpect(jsonPath("$.content[0].source.name").value(source.get("name").asText()))
        .andExpect(jsonPath("$.content[0].owner.email").value(email))
        .andExpect(jsonPath("$.content[0].estimatedValue").value("1250.00"));

    // Omitted optional fields are cleared (the priority falls back to MEDIUM, the currency goes
    // with its value); the status stays.
    Map<String, Object> edit = edit("Ada", userId(email), 0, "email", "ada@lovelace.example");
    send(token, put(LEAD, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.lastName").doesNotExist())
        .andExpect(jsonPath("$.companyName").doesNotExist())
        .andExpect(jsonPath("$.email").value("ada@lovelace.example"))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.followUpDate").doesNotExist())
        .andExpect(jsonPath("$.estimatedValue").doesNotExist())
        .andExpect(jsonPath("$.currency").doesNotExist())
        .andExpect(jsonPath("$.notes").doesNotExist())
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(LEAD, w, id), edit).andExpect(jsonPath("$.version").value(1));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(1).getEntityLabel()).isEqualTo("Ada Lovelace");
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Ada");
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys(
            "lastName",
            "companyName",
            "email",
            "phone",
            "jobTitle",
            "source",
            "priority",
            "followUpDate",
            "estimatedValue",
            "currency",
            "notes");
  }

  @Test
  void validation() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    invalid(token, w, Map.of(), "firstName");
    invalid(token, w, Map.of("firstName", "　 "), "firstName");
    invalid(token, w, Map.of("firstName", "x".repeat(101)), "firstName");
    invalid(token, w, Map.of("firstName", "Ada", "lastName", "x".repeat(101)), "lastName");
    invalid(token, w, Map.of("firstName", "Ada", "companyName", "x".repeat(201)), "companyName");
    invalid(token, w, Map.of("firstName", "Ada", "email", "ada"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "email", "a b@example.com"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "email", "ada@example"), "email");
    invalid(token, w, Map.of("firstName", "Ada", "phone", "1".repeat(51)), "phone");
    invalid(token, w, Map.of("firstName", "Ada", "jobTitle", "x".repeat(101)), "jobTitle");
    invalid(token, w, Map.of("firstName", "Ada", "notes", "x".repeat(5001)), "notes");
    // Not even readable (no field error): an unknown priority, a date that isn't one.
    malformed(token, w, Map.of("firstName", "Ada", "priority", "URGENT"));
    malformed(token, w, Map.of("firstName", "Ada", "followUpDate", "next week"));
    malformed(token, w, Map.of("firstName", "Ada", "followUpDate", "2026-02-30"));

    long id = create(token, w, Map.of("firstName", "Ada"));
    send(token, put(LEAD, w, id), Map.of("firstName", "Ada"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    list(token, w).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void estimatedValueAndCurrency() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");

    // A value alone takes the workspace's currency (EUR); the response shows two fraction digits
    // as a string, whether the request sent a string or a number.
    long id =
        body(send(token, post(LEADS, w), Map.of("firstName", "Ada", "estimatedValue", "1250"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedValue").value("1250.00"))
                .andExpect(jsonPath("$.currency").value("EUR")))
            .get("id")
            .asLong();
    send(
            token,
            post(LEADS, w),
            Map.of("firstName", "Bob", "estimatedValue", 99.5, "currency", "GBP"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estimatedValue").value("99.50"))
        .andExpect(jsonPath("$.currency").value("GBP"));
    send(token, post(LEADS, w), Map.of("firstName", "Cy", "estimatedValue", "0"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estimatedValue").value("0.00"));
    send(token, post(LEADS, w), Map.of("firstName", "Dee", "estimatedValue", "9999999999999.99"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estimatedValue").value("9999999999999.99"));

    // 1250 is 1250.00: no phantom change, no audit event.
    send(token, put(LEAD, w, id), edit("Ada", userId(email), 0, "estimatedValue", 1250))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    assertThat(trail(w, id)).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);
    // A changed value is one; the currency stays the workspace's.
    send(token, put(LEAD, w, id), edit("Ada", userId(email), 0, "estimatedValue", "1250.50"))
        .andExpect(jsonPath("$.estimatedValue").value("1250.50"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(trail(w, id).get(0).getChanges()).containsOnlyKeys("estimatedValue");

    // Both or neither: a currency alone is a 400 on the value.
    send(token, post(LEADS, w), Map.of("firstName", "Eve", "currency", "USD"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.estimatedValue").exists());
    send(token, put(LEAD, w, id), edit("Ada", userId(email), 1, "currency", "USD"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.estimatedValue").exists());
    // A blank currency counts as none.
    send(token, post(LEADS, w), Map.of("firstName", "Fay", "currency", "　"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").doesNotExist());
    send(token, post(LEADS, w), Map.of("firstName", "Gus", "estimatedValue", "5", "currency", " "))
        .andExpect(jsonPath("$.currency").value("EUR"));

    invalid(token, w, Map.of("firstName", "x", "estimatedValue", "-1"), "estimatedValue");
    invalid(token, w, Map.of("firstName", "x", "estimatedValue", "1.005"), "estimatedValue");
    invalid(token, w, Map.of("firstName", "x", "estimatedValue", "1.500"), "estimatedValue");
    invalid(
        token, w, Map.of("firstName", "x", "estimatedValue", "10000000000000"), "estimatedValue");
    malformed(token, w, Map.of("firstName", "x", "estimatedValue", "lots"));
    invalid(
        token, w, Map.of("firstName", "x", "estimatedValue", "1", "currency", "ZZZ"), "currency");
    invalid(
        token, w, Map.of("firstName", "x", "estimatedValue", "1", "currency", "EURO"), "currency");
  }

  @Test
  void aPutWithoutACurrencyResetsItToTheWorkspaceDefault() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long id =
        create(token, w, Map.of("firstName", "Ada", "estimatedValue", "100", "currency", "USD"));

    // A PUT replaces every field: with a value and no currency, the workspace's (EUR) applies.
    send(token, put(LEAD, w, id), edit("Ada", userId(email), 0, "estimatedValue", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedValue").value("100.00"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.version").value(1));
    AuditEvent update = trail(w, id).get(0);
    assertThat(update.getAction()).isEqualTo(AuditAction.UPDATE);
    assertThat(update.getChanges())
        .containsOnlyKeys("currency")
        .containsEntry("currency", Map.of("from", "USD", "to", "EUR"));
  }

  @Test
  void anEditKeepsTheStatusAndTheReason() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));
    setStatus(token, w, id, "UNQUALIFIED", "No budget", 0).andExpect(status().isOk());

    send(token, put(LEAD, w, id), edit("Ada B", userId(email), 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ada B"))
        .andExpect(jsonPath("$.status").value("UNQUALIFIED"))
        .andExpect(jsonPath("$.unqualifiedReason").value("No budget"))
        .andExpect(jsonPath("$.version").value(2));
    send(token, get(LEAD, w, id))
        .andExpect(jsonPath("$.status").value("UNQUALIFIED"))
        .andExpect(jsonPath("$.unqualifiedReason").value("No budget"));
    assertThat(trail(w, id).get(0).getChanges()).containsOnlyKeys("firstName");
  }

  @Test
  void sourceFromAnotherWorkspaceIs400() throws Exception {
    String email = uniqueEmail();
    String a = tokenFor(email);
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirSource = firstSource(b, wb).get("id").asLong();

    send(a, post(LEADS, wa), Map.of("firstName", "Ada", "sourceId", theirSource))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.sourceId").value("is not found in this workspace"));
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));

    long own = create(a, wa, Map.of("firstName", "Ada"));
    send(a, put(LEAD, wa, own), edit("Ada", userId(email), 0, "sourceId", theirSource))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.sourceId").exists());
    send(a, get(LEAD, wa, own))
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void convertedLinksStayInTheWorkspace() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirCompany = id(send(b, post(COMPANIES, wb), Map.of("name", "B's")));
    long theirContact = id(send(b, post(CONTACTS, wb), Map.of("firstName", "B")));
    long own = create(a, wa, Map.of("firstName", "Ada"));

    // The conversion links only rows it found here; the composite foreign keys are the backstop.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE leads SET converted_company_id = ? WHERE id = ?", theirCompany, own))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE leads SET converted_contact_id = ? WHERE id = ?", theirContact, own))
        .isInstanceOf(DataIntegrityViolationException.class);
    send(a, get(LEAD, wa, own))
        .andExpect(jsonPath("$.convertedCompany").doesNotExist())
        .andExpect(jsonPath("$.convertedContact").doesNotExist());
  }

  @Test
  void statusTransitions() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));

    setStatus(token, w, id, "CONTACTED", null, 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONTACTED"))
        .andExpect(jsonPath("$.version").value(1));
    setStatus(token, w, id, "QUALIFIED", null, 1)
        .andExpect(jsonPath("$.status").value("QUALIFIED"))
        .andExpect(jsonPath("$.version").value(2));
    setStatus(token, w, id, "UNQUALIFIED", "  Too small　", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNQUALIFIED"))
        .andExpect(jsonPath("$.unqualifiedReason").value("Too small"))
        .andExpect(jsonPath("$.version").value(3));
    // Any open status reachable from any other, and leaving UNQUALIFIED forgets the reason.
    setStatus(token, w, id, "NEW", "ignored", 3)
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.unqualifiedReason").doesNotExist())
        .andExpect(jsonPath("$.version").value(4));
    send(token, get(LEAD, w, id))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.unqualifiedReason").doesNotExist());

    // A reason sent with another status than UNQUALIFIED is dropped, not stored.
    setStatus(token, w, id, "QUALIFIED", "because", 4)
        .andExpect(jsonPath("$.unqualifiedReason").doesNotExist());

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            AuditAction.STATUS_CHANGE,
            AuditAction.STATUS_CHANGE,
            AuditAction.STATUS_CHANGE,
            AuditAction.STATUS_CHANGE,
            AuditAction.STATUS_CHANGE,
            AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys("status")
        .containsEntry("status", Map.of("from", "NEW", "to", "QUALIFIED"));
    assertThat(trail.get(1).getChanges())
        .containsOnlyKeys("status", "unqualifiedReason")
        .containsEntry("status", Map.of("from", "UNQUALIFIED", "to", "NEW"));
    assertThat(trail.get(2).getChanges())
        .containsEntry("status", Map.of("from", "QUALIFIED", "to", "UNQUALIFIED"))
        .containsKey("unqualifiedReason");
    assertThat(trail.get(2).getEntityLabel()).isEqualTo("Ada");
  }

  @Test
  void statusRules() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));

    // UNQUALIFIED needs a reason that is not blank, however it is spelled.
    setStatus(token, w, id, "UNQUALIFIED", null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").exists());
    setStatus(token, w, id, "UNQUALIFIED", "　 ", 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").exists());
    setStatus(token, w, id, "UNQUALIFIED", "x".repeat(501), 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").exists());
    setStatus(token, w, id, "UNQUALIFIED", "x".repeat(500), 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unqualifiedReason").value("x".repeat(500)));
    // CONVERTED is only reached through conversion, whatever the version.
    setStatus(token, w, id, "CONVERTED", null, 1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.status").exists());
    setStatus(token, w, id, "CONVERTED", null, 0).andExpect(status().isBadRequest());
    // The request needs a status that exists and the version last read.
    send(token, post(LEAD + "/status", w, id), Map.of("version", 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.status").exists());
    setStatus(token, w, id, "ARCHIVED", null, 1).andExpect(status().isBadRequest());
    send(token, post(LEAD + "/status", w, id), Map.of("status", "NEW"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    // A stale version is a conflict.
    setStatus(token, w, id, "NEW", null, 0).andExpect(status().isConflict());

    // The same status again changes nothing: no new version, no audit event.
    setStatus(token, w, id, "UNQUALIFIED", "x".repeat(500), 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    setStatus(token, w, id, "NEW", null, 1).andExpect(jsonPath("$.version").value(2));
    setStatus(token, w, id, "NEW", null, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    assertThat(trail(w, id))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.STATUS_CHANGE, AuditAction.STATUS_CHANGE, AuditAction.CREATE);

    long other = create(token, w, Map.of("firstName", "Bob"));
    setStatus(token, w, other, "NEW", null, 0).andExpect(jsonPath("$.version").value(0));
    assertThat(trail(w, other)).hasSize(1);
  }

  @Test
  void reEnteringUnqualifiedWithANewReasonEditsTheReason() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));
    setStatus(token, w, id, "UNQUALIFIED", "No budget", 0)
        .andExpect(jsonPath("$.version").value(1));

    // The same status with a different reason (stripped) saves it: a new version and an audit
    // event that shows only the reason.
    setStatus(token, w, id, "UNQUALIFIED", "  Went with a rival　", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNQUALIFIED"))
        .andExpect(jsonPath("$.unqualifiedReason").value("Went with a rival"))
        .andExpect(jsonPath("$.version").value(2));
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.STATUS_CHANGE, AuditAction.STATUS_CHANGE, AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys("unqualifiedReason")
        .containsEntry("unqualifiedReason", Map.of("from", "No budget", "to", "Went with a rival"));

    // Every other same-status call is a no-op: the reason as it is (stripped), none, a blank one.
    for (String reason : new String[] {"Went with a rival", " Went with a rival ", null, "　 "}) {
      setStatus(token, w, id, "UNQUALIFIED", reason, 2)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.unqualifiedReason").value("Went with a rival"))
          .andExpect(jsonPath("$.version").value(2));
    }
    // A reason sent with a status that has none changes nothing either.
    setStatus(token, w, id, "NEW", null, 2).andExpect(jsonPath("$.version").value(3));
    setStatus(token, w, id, "NEW", "ignored", 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unqualifiedReason").doesNotExist())
        .andExpect(jsonPath("$.version").value(3));
    assertThat(trail(w, id)).hasSize(4);
  }

  @Test
  void convertedLeadIsReadOnly() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme")));
    long contact = id(send(token, post(CONTACTS, w), Map.of("firstName", "Ada", "lastName", "L")));
    long id = create(token, w, Map.of("firstName", "Ada"));
    setStatus(token, w, id, "QUALIFIED", null, 0).andExpect(status().isOk());
    // Converted directly in the database: LeadConversionIT covers the conversion itself.
    jdbc.update(
        "UPDATE leads SET status = 'CONVERTED', converted_at = now(), converted_company_id = ?,"
            + " converted_contact_id = ?, version = version + 1 WHERE id = ?",
        acme,
        contact,
        id);

    send(token, get(LEAD, w, id))
        .andExpect(jsonPath("$.status").value("CONVERTED"))
        .andExpect(jsonPath("$.convertedAt").exists())
        .andExpect(jsonPath("$.convertedCompany.id").value(acme))
        .andExpect(jsonPath("$.convertedCompany.name").value("Acme"))
        .andExpect(jsonPath("$.convertedContact.id").value(contact))
        .andExpect(jsonPath("$.convertedContact.fullName").value("Ada L"))
        .andExpect(jsonPath("$.convertedDeal").doesNotExist())
        .andExpect(jsonPath("$.version").value(2));
    list(token, w)
        .andExpect(jsonPath("$.content[0].convertedCompany.name").value("Acme"))
        .andExpect(jsonPath("$.content[0].convertedContact.fullName").value("Ada L"));
    list(token, w, "status", "CONVERTED").andExpect(names("Ada"));

    send(token, put(LEAD, w, id), edit("Edited", userId(email), 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Converted leads can't be edited"));
    // Even with a stale version, the answer is that a converted lead can't be edited.
    send(token, put(LEAD, w, id), edit("Edited", userId(email), 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Converted leads can't be edited"));
    for (String target : List.of("NEW", "CONTACTED", "QUALIFIED", "UNQUALIFIED")) {
      setStatus(token, w, id, target, "reason", 2).andExpect(status().isConflict());
    }
    // Likewise with a stale version: the lead is converted, not changed by someone else.
    setStatus(token, w, id, "NEW", null, 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Converted leads can't change status"));
    // CONVERTED as a target is still the request's mistake.
    setStatus(token, w, id, "CONVERTED", null, 2).andExpect(status().isBadRequest());
    send(token, get(LEAD, w, id)).andExpect(jsonPath("$.firstName").value("Ada"));

    // Archiving is housekeeping, not an edit: it works on a converted lead, and so does restoring.
    send(token, post(LEAD + "/archive", w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.status").value("CONVERTED"));
    list(token, w, "archived", "true").andExpect(names("Ada"));
    send(token, post(LEAD + "/restore", w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.status").value("CONVERTED"))
        .andExpect(jsonPath("$.convertedCompany.id").value(acme));
  }

  @Test
  void filterByStatusAndFollowUp() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("firstName", "A", "followUpDate", "2026-11-01"));
    long b = create(token, w, Map.of("firstName", "B", "followUpDate", "2026-11-05"));
    long c = create(token, w, Map.of("firstName", "C", "followUpDate", "2026-11-10"));
    create(token, w, Map.of("firstName", "D"));
    setStatus(token, w, b, "CONTACTED", null, 0).andExpect(status().isOk());
    setStatus(token, w, c, "UNQUALIFIED", "No budget", 0).andExpect(status().isOk());

    list(token, w, "status", "NEW").andExpect(namesAnyOrder("A", "D"));
    list(token, w, "status", "CONTACTED").andExpect(names("B"));
    // Repeatable: any of the given statuses.
    list(token, w, "status", "CONTACTED", "status", "UNQUALIFIED")
        .andExpect(namesAnyOrder("B", "C"))
        .andExpect(jsonPath("$.totalElements").value(2));
    list(token, w, "status", "NEW", "status", "CONTACTED", "status", "UNQUALIFIED")
        .andExpect(jsonPath("$.totalElements").value(4));
    list(token, w, "status", "QUALIFIED").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "status", "SOLD").andExpect(status().isBadRequest());

    // Inclusive; leads without a follow-up date never match.
    list(token, w, "followUpBefore", "2026-11-05").andExpect(namesAnyOrder("A", "B"));
    list(token, w, "followUpBefore", "2026-11-04").andExpect(names("A"));
    list(token, w, "followUpBefore", "2026-10-31").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "followUpBefore", "2027-01-01").andExpect(jsonPath("$.totalElements").value(3));
    list(token, w, "followUpBefore", "2026-11-10", "status", "NEW", "status", "CONTACTED")
        .andExpect(namesAnyOrder("A", "B"));
    list(token, w, "followUpBefore", "tomorrow").andExpect(status().isBadRequest());
  }

  @Test
  void filterByOwnerSourceAndPriority() throws Exception {
    String email = uniqueEmail();
    String owner = tokenFor(email);
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    JsonNode source = firstSource(owner, w);
    create(owner, w, Map.of("firstName", "Mine", "priority", "HIGH"));
    create(
        owner,
        w,
        Map.of("firstName", "Sourced", "sourceId", source.get("id").asLong(), "priority", "LOW"));
    create(rep, w, Map.of("firstName", "Rep's"));

    list(owner, w, "ownerId", String.valueOf(userId(repEmail))).andExpect(names("Rep's"));
    list(owner, w, "sourceId", source.get("id").asText()).andExpect(names("Sourced"));
    list(owner, w, "priority", "HIGH").andExpect(names("Mine"));
    list(owner, w, "priority", "LOW").andExpect(names("Sourced"));
    list(owner, w, "priority", "MEDIUM").andExpect(names("Rep's"));
    list(owner, w, "priority", "URGENT").andExpect(status().isBadRequest());
  }

  @Test
  void search() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(
        token,
        w,
        Map.of(
            "firstName", "Ada",
            "lastName", "Lovelace",
            "companyName", "Analytical Engines",
            "phone", "+66 2 123 4567",
            "email", "ada@analytical.example"));
    create(token, w, Map.of("firstName", "Bob", "lastName", "Builder", "email", "bob@b.example"));
    create(token, w, Map.of("firstName", "Cy"));

    list(token, w, "q", "ENGINES").andExpect(names("Ada"));
    list(token, w, "q", "2 123").andExpect(names("Ada"));
    list(token, w, "q", " ada love ").andExpect(names("Ada"));
    list(token, w, "q", "ANALYTICAL.").andExpect(names("Ada"));
    list(token, w, "q", "build").andExpect(names("Bob"));
    list(token, w, "q", "b.example").andExpect(names("Bob"));
    list(token, w, "q", "cy").andExpect(names("Cy"));
    list(token, w, "q", "%").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", "_").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", " ").andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void sortByNameAndDates() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("firstName", "Bob", "lastName", "smith"));
    create(token, w, Map.of("firstName", "alice", "lastName", "Smith"));
    create(token, w, Map.of("firstName", "Zed"));
    create(token, w, Map.of("firstName", "Carl", "lastName", "Adams"));

    // The default: newest first (CREATED_AT and UPDATED_AT descend unless told otherwise).
    list(token, w).andExpect(names("Carl", "Zed", "alice", "Bob"));
    list(token, w, "sort", "UPDATED_AT").andExpect(names("Carl", "Zed", "alice", "Bob"));
    list(token, w, "direction", "ASC").andExpect(names("Bob", "alice", "Zed", "Carl"));
    // Last name, then first name, ignoring case; no last name: last ascending, first descending.
    list(token, w, "sort", "NAME").andExpect(names("Carl", "alice", "Bob", "Zed"));
    list(token, w, "sort", "NAME", "direction", "DESC")
        .andExpect(names("Zed", "Bob", "alice", "Carl"));
    list(token, w, "sort", "NAME", "size", "3", "page", "1")
        .andExpect(names("Zed"))
        .andExpect(jsonPath("$.totalElements").value(4));
    list(token, w, "sort", "UPDATED_AT", "direction", "ASC")
        .andExpect(names("Bob", "alice", "Zed", "Carl"));
    list(token, w, "sort", "name").andExpect(status().isBadRequest());
  }

  @Test
  void sortByFollowUpDateListsLeadsWithoutOneLast() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("firstName", "None1"));
    create(token, w, Map.of("firstName", "Mid", "followUpDate", "2026-11-05"));
    create(token, w, Map.of("firstName", "Late", "followUpDate", "2026-12-01"));
    create(token, w, Map.of("firstName", "None2"));
    create(token, w, Map.of("firstName", "Early", "followUpDate", "2026-11-01"));

    // Without a direction the soonest comes first.
    list(token, w, "sort", "FOLLOW_UP_DATE")
        .andExpect(names("Early", "Mid", "Late", "None1", "None2"));
    // No date: last in both directions, in the id tie-breaker's direction.
    list(token, w, "sort", "FOLLOW_UP_DATE", "direction", "ASC")
        .andExpect(names("Early", "Mid", "Late", "None1", "None2"));
    list(token, w, "sort", "FOLLOW_UP_DATE", "direction", "DESC")
        .andExpect(names("Late", "Mid", "Early", "None2", "None1"));
    list(token, w, "sort", "FOLLOW_UP_DATE", "direction", "ASC", "size", "2", "page", "1")
        .andExpect(names("Late", "None1"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3));
    list(token, w, "sort", "FOLLOW_UP_DATE", "direction", "DESC", "size", "2", "page", "2")
        .andExpect(names("None1"))
        .andExpect(jsonPath("$.totalElements").value(5));
    // With filters, the order and the total still agree.
    list(token, w, "sort", "FOLLOW_UP_DATE", "followUpBefore", "2026-11-05", "size", "1")
        .andExpect(names("Early"))
        .andExpect(jsonPath("$.totalElements").value(2));
    list(token, w, "sort", "FOLLOW_UP_DATE", "q", "none", "direction", "DESC")
        .andExpect(names("None2", "None1"));
  }

  @Test
  void sortByPriorityListsHighFirstDescending() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("firstName", "Med1"));
    create(token, w, Map.of("firstName", "High1", "priority", "HIGH"));
    create(token, w, Map.of("firstName", "Low1", "priority", "LOW"));
    create(token, w, Map.of("firstName", "High2", "priority", "HIGH"));
    create(token, w, Map.of("firstName", "Low2", "priority", "LOW"));
    create(token, w, Map.of("firstName", "Med2", "priority", "MEDIUM"));

    // HIGH > MEDIUM > LOW; descending (the default) is "most urgent first", ties by id in the same
    // direction.
    list(token, w, "sort", "PRIORITY")
        .andExpect(names("High2", "High1", "Med2", "Med1", "Low2", "Low1"));
    list(token, w, "sort", "PRIORITY", "direction", "DESC")
        .andExpect(names("High2", "High1", "Med2", "Med1", "Low2", "Low1"));
    list(token, w, "sort", "PRIORITY", "direction", "ASC")
        .andExpect(names("Low1", "Low2", "Med1", "Med2", "High1", "High2"));
    // Pages follow the same order and the total counts every lead.
    list(token, w, "sort", "PRIORITY", "direction", "DESC", "size", "4")
        .andExpect(names("High2", "High1", "Med2", "Med1"))
        .andExpect(jsonPath("$.totalElements").value(6))
        .andExpect(jsonPath("$.totalPages").value(2));
    list(token, w, "sort", "PRIORITY", "size", "0").andExpect(status().isBadRequest());
    list(token, w, "sort", "FOLLOW_UP_DATE", "page", "-1").andExpect(status().isBadRequest());
    list(token, w, "sort", "PRIORITY", "direction", "DESC", "size", "4", "page", "1")
        .andExpect(names("Low2", "Low1"))
        .andExpect(jsonPath("$.totalElements").value(6));
    list(token, w, "sort", "PRIORITY", "direction", "ASC", "size", "4", "page", "1")
        .andExpect(names("High1", "High2"))
        .andExpect(jsonPath("$.totalElements").value(6));
    // With a filter the total follows it.
    list(token, w, "sort", "PRIORITY", "direction", "DESC", "q", "1", "size", "2")
        .andExpect(names("High1", "Med1"))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void archiveHidesAndRestoreReturns() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long a = create(token, w, Map.of("firstName", "A"));
    create(token, w, Map.of("firstName", "B"));

    send(token, post(LEAD + "/archive", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.archivedAt").exists())
        .andExpect(jsonPath("$.version").value(1));
    send(token, post(LEAD + "/archive", w, a)).andExpect(jsonPath("$.version").value(1));
    list(token, w).andExpect(names("B"));
    list(token, w, "archived", "true").andExpect(names("A"));

    send(token, post(LEAD + "/restore", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.version").value(2));
    send(token, post(LEAD + "/restore", w, a)).andExpect(jsonPath("$.version").value(2));
    list(token, w).andExpect(names("B", "A"));
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
        body(send(rep, post(LEADS, w), Map.of("firstName", "Rep's"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.userId").value(repId)))
            .get("id")
            .asLong();
    send(rep, put(LEAD, w, own), edit("Rep's own", repId, 0)).andExpect(status().isOk());
    setStatus(rep, w, own, "CONTACTED", null, 1).andExpect(status().isOk());

    long managers = create(manager, w, Map.of("firstName", "Manager's"));
    send(rep, get(LEAD, w, managers)).andExpect(status().isOk());
    send(rep, put(LEAD, w, managers), edit("Taken", repId, 0)).andExpect(status().isForbidden());
    send(rep, put(LEAD, w, managers), edit("Taken", managerId, 0))
        .andExpect(status().isForbidden());
    setStatus(rep, w, managers, "CONTACTED", null, 0).andExpect(status().isForbidden());
    send(rep, post(LEAD + "/archive", w, managers)).andExpect(status().isForbidden());
    send(rep, post(LEAD + "/restore", w, managers)).andExpect(status().isForbidden());
    send(rep, post(LEADS, w), Map.of("firstName", "Gift", "ownerId", managerId))
        .andExpect(status().isForbidden());
    send(rep, put(LEAD, w, own), edit("Rep's own", null, 2)).andExpect(status().isForbidden());
    send(manager, get(LEAD, w, managers))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.version").value(0));

    send(manager, put(LEAD, w, own), edit("Rep's own", managerId, 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner.userId").value(managerId));
    setStatus(manager, w, own, "QUALIFIED", null, 3).andExpect(status().isOk());
    send(manager, post(LEADS, w), Map.of("firstName", "For rep", "ownerId", repId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.owner.email").value(repEmail));
    send(manager, post(LEADS, w), Map.of("firstName", "Nope", "ownerId", userId(outsiderEmail)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    // Now the manager's, so the rep can't change or archive it any more.
    setStatus(rep, w, own, "CONTACTED", null, 4).andExpect(status().isForbidden());
    send(rep, post(LEAD + "/archive", w, own)).andExpect(status().isForbidden());
  }

  @Test
  void staleVersionIs409() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long id = create(token, w, Map.of("firstName", "Ada"));

    send(token, put(LEAD, w, id), edit("First", userId(email), 0)).andExpect(status().isOk());
    send(token, put(LEAD, w, id), edit("Second", userId(email), 0))
        .andExpect(status().isConflict());

    send(token, get(LEAD, w, id)).andExpect(jsonPath("$.firstName").value("First"));
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long ofB = create(b, wb, Map.of("firstName", "B's"));

    send(a, get(LEAD, wa, ofB)).andExpect(status().isNotFound());
    send(a, put(LEAD, wa, ofB), edit("Hijacked", null, 0)).andExpect(status().isNotFound());
    setStatus(a, wa, ofB, "CONTACTED", null, 0).andExpect(status().isNotFound());
    send(a, post(LEAD + "/archive", wa, ofB)).andExpect(status().isNotFound());
    send(a, post(LEAD + "/restore", wa, ofB)).andExpect(status().isNotFound());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));

    send(b, get(LEAD, wb, ofB))
        .andExpect(jsonPath("$.firstName").value("B's"))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void removingAMemberLeavesTheirLeadsUnassigned() throws Exception {
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

    send(ownerToken, get(LEAD, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Rep's"))
        .andExpect(jsonPath("$.owner").doesNotExist());
    list(ownerToken, w).andExpect(names("Rep's"));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(LEADS, w), input));
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
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

  private ResultActions setStatus(
      String token, long w, long id, String status, String reason, long v) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("status", status);
    body.put("reason", reason);
    body.put("version", v);
    return send(token, post(LEAD + "/status", w, id), body);
  }

  private void malformed(String token, long w, Map<String, ?> input) throws Exception {
    send(token, post(LEADS, w), input).andExpect(status().isBadRequest());
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(LEADS, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    return send(token, withParams(get(LEADS, w), params));
  }

  private static MockHttpServletRequestBuilder withParams(
      MockHttpServletRequestBuilder request, String... params) {
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return request;
  }

  private static ResultMatcher names(String... firstNames) {
    return jsonPath("$.content[*].firstName", contains(firstNames));
  }

  private static ResultMatcher namesAnyOrder(String... firstNames) {
    return jsonPath("$.content[*].firstName", org.hamcrest.Matchers.containsInAnyOrder(firstNames));
  }

  /** The lead's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.LEAD && e.getEntityId() == id)
        .toList();
  }
}
