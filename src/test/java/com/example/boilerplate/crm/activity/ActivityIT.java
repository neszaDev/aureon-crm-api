package com.example.boilerplate.crm.activity;

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

class ActivityIT extends WorkspaceTestSupport {
  static final String ACTIVITIES = "/api/v1/workspaces/{workspaceId}/activities";
  static final String ACTIVITY = ACTIVITIES + "/{id}";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";
  static final String NEEDS_A_LINK = "Link the activity to a company, contact, lead or deal";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;

  /** A signed-in member of a workspace. */
  record Member(String email, String token, long userId) {}

  @Test
  void logCallOnContactFillsCompany() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));
    long contact =
        record(
            token,
            CONTACTS,
            w,
            Map.of("firstName", "Peter", "lastName", "Gibbons", "companyId", company));

    Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
    JsonNode created =
        body(
            send(
                    token,
                    post(ACTIVITIES, w),
                    Map.of(
                        "type",
                        "CALL",
                        "subject",
                        " Intro call　",
                        "body",
                        " Went well ",
                        "contactId",
                        contact))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CALL"))
                .andExpect(jsonPath("$.subject").value("Intro call"))
                .andExpect(jsonPath("$.body").value("Went well"))
                .andExpect(jsonPath("$.author.userId").value(userId(email)))
                .andExpect(jsonPath("$.author.email").value(email))
                .andExpect(jsonPath("$.contact.id").value(contact))
                .andExpect(jsonPath("$.contact.fullName").value("Peter Gibbons"))
                // The contact's company, which the request didn't name.
                .andExpect(jsonPath("$.company.id").value(company))
                .andExpect(jsonPath("$.company.name").value("Initech"))
                .andExpect(jsonPath("$.lead").doesNotExist())
                .andExpect(jsonPath("$.deal").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)));
    long id = created.get("id").asLong();

    // Not given: occurredAt is now, in microseconds as stored.
    Instant occurredAt = Instant.parse(created.get("occurredAt").asText());
    assertThat(occurredAt).isBetween(before, Instant.now());
    assertThat(occurredAt).isEqualTo(occurredAt.truncatedTo(ChronoUnit.MICROS));
    // The answer equals a later read and the list's entry.
    assertThat(body(send(token, get(ACTIVITY, w, id)))).isEqualTo(created);
    assertThat(body(list(token, w)).get("content").get(0)).isEqualTo(created);

    // Every create writes one CREATE event with no changes and the subject as its label.
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Intro call");
    assertThat(trail.get(0).getChanges()).isEmpty();
  }

  @Test
  void createGetUpdateWithLinks() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));
    long contact = record(token, CONTACTS, w, Map.of("firstName", "Peter", "lastName", "Gibbons"));
    long lead = record(token, LEADS, w, Map.of("firstName", "Ada", "lastName", "Lovelace"));
    long deal = record(token, DEALS, w, Map.of("name", "TPS reports", "amount", "1"));
    Map<String, Object> input = new HashMap<>();
    input.put("type", "MEETING");
    input.put("subject", "Kickoff");
    input.put("body", "Agenda sent");
    input.put("occurredAt", "2031-03-04T10:15:30Z");
    input.put("companyId", company);
    input.put("contactId", contact);
    input.put("leadId", lead);
    input.put("dealId", deal);

    long id =
        body(send(token, post(ACTIVITIES, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MEETING"))
                .andExpect(jsonPath("$.occurredAt").value("2031-03-04T10:15:30Z"))
                .andExpect(jsonPath("$.company.name").value("Initech"))
                .andExpect(jsonPath("$.contact.fullName").value("Peter Gibbons"))
                .andExpect(jsonPath("$.lead.id").value(lead))
                .andExpect(jsonPath("$.lead.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.deal.id").value(deal))
                .andExpect(jsonPath("$.deal.name").value("TPS reports")))
            .get("id")
            .asLong();
    // The list returns full activities, links included.
    list(token, w)
        .andExpect(jsonPath("$.content[0].subject").value("Kickoff"))
        .andExpect(jsonPath("$.content[0].company.name").value("Initech"))
        .andExpect(jsonPath("$.content[0].contact.fullName").value("Peter Gibbons"))
        .andExpect(jsonPath("$.content[0].lead.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.content[0].deal.name").value("TPS reports"))
        .andExpect(jsonPath("$.content[0].author.email").value(email));

    // Every field is replaced: the omitted body is cleared, the type changes, only the deal stays.
    Map<String, Object> edit = edit("EMAIL", "Kickoff, recapped", 0);
    edit.put("occurredAt", "2031-03-05T08:00:00Z");
    edit.put("dealId", deal);
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("EMAIL"))
        .andExpect(jsonPath("$.subject").value("Kickoff, recapped"))
        .andExpect(jsonPath("$.body").doesNotExist())
        .andExpect(jsonPath("$.occurredAt").value("2031-03-05T08:00:00Z"))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.lead").doesNotExist())
        .andExpect(jsonPath("$.deal.id").value(deal))
        .andExpect(jsonPath("$.author.userId").value(userId(email)))
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(ACTIVITY, w, id), edit).andExpect(jsonPath("$.version").value(1));
    // A stale version is a 409 that writes nothing.
    edit.put("version", 0);
    edit.put("subject", "Lost update");
    send(token, put(ACTIVITY, w, id), edit).andExpect(status().isConflict());
    send(token, get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.subject").value("Kickoff, recapped"))
        .andExpect(jsonPath("$.version").value(1));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys("type", "subject", "body", "occurredAt", "company", "contact", "lead")
        .containsEntry("type", Map.of("from", "MEETING", "to", "EMAIL"));
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Kickoff, recapped");
  }

  @Test
  void validation() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));

    invalid(token, w, activity(null, "x", company), "type");
    invalid(token, w, activity("CALL", null, company), "subject");
    invalid(token, w, activity("CALL", "　 ", company), "subject");
    invalid(token, w, activity("CALL", "x".repeat(201), company), "subject");
    Map<String, Object> longBody = activity("CALL", "x", company);
    longBody.put("body", "b".repeat(5001));
    invalid(token, w, longBody, "body");
    malformed(token, w, activity("PHONE", "x", company));
    Map<String, Object> badTime = activity("CALL", "x", company);
    badTime.put("occurredAt", "yesterday");
    malformed(token, w, badTime);
    badTime.put("occurredAt", "2026-02-30T10:00:00Z");
    malformed(token, w, badTime);
    Map<String, Object> badLink = activity("CALL", "x", company);
    badLink.put("contactId", "peter");
    malformed(token, w, badLink);
    // Exactly at the limits.
    Map<String, Object> atLimits = activity("NOTE", "x".repeat(200), company);
    atLimits.put("body", "b".repeat(5000));
    send(token, post(ACTIVITIES, w), atLimits).andExpect(status().isCreated());

    long id = create(token, w, activity("NOTE", "Note", company));
    send(token, put(ACTIVITY, w, id), activity("NOTE", "Note", company))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    Map<String, Object> blank = edit("NOTE", "　", 0);
    blank.put("companyId", company);
    send(token, put(ACTIVITY, w, id), blank)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.subject").exists());
    list(token, w).andExpect(jsonPath("$.totalElements").value(2));
    send(token, get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.subject").value("Note"))
        .andExpect(jsonPath("$.version").value(0));
  }

  /** Instants are stored in microseconds: the answer equals a later read. */
  @Test
  void occurredAtKeepsMicrosecondsAsStored() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));
    Map<String, Object> input = activity("CALL", "Precise", company);
    input.put("occurredAt", "2031-03-04T10:15:30.123456789Z");

    long id =
        body(send(token, post(ACTIVITIES, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurredAt").value("2031-03-04T10:15:30.123456Z")))
            .get("id")
            .asLong();
    send(token, get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.occurredAt").value("2031-03-04T10:15:30.123456Z"));

    // The nanoseconds are not a change: same version, no UPDATE event. Nor is another offset.
    Map<String, Object> edit = edit("CALL", "Precise", 0);
    edit.put("companyId", company);
    edit.put("occurredAt", "2031-03-04T11:15:30.123456+01:00");
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    assertThat(trail(w, id)).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);

    // An edit that leaves the time out keeps it (now is only the default of a new activity).
    edit.remove("occurredAt");
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(jsonPath("$.occurredAt").value("2031-03-04T10:15:30.123456Z"))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void needsALink() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));
    long id = create(token, w, activity("CALL", "Linked", company));

    Map<String, Object> none = new HashMap<>();
    none.put("type", "CALL");
    none.put("subject", "Nowhere");
    send(token, post(ACTIVITIES, w), none)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.links").value(NEEDS_A_LINK));
    // Explicit nulls are no links either.
    for (String link : List.of("companyId", "contactId", "leadId", "dealId")) none.put(link, null);
    send(token, post(ACTIVITIES, w), none)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.links").value(NEEDS_A_LINK));
    // An edit can't drop the last link either.
    send(token, put(ACTIVITY, w, id), edit("CALL", "Linked", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.links").value(NEEDS_A_LINK));
    list(token, w).andExpect(jsonPath("$.totalElements").value(1));
    send(token, get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.company.id").value(company))
        .andExpect(jsonPath("$.version").value(0));

    // Any one link is enough, whichever it is.
    long contact = record(token, CONTACTS, w, Map.of("firstName", "Peter"));
    long lead = record(token, LEADS, w, Map.of("firstName", "Ada"));
    long deal = record(token, DEALS, w, Map.of("name", "Big", "amount", "1"));
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a contact", "contactId", contact))
        .andExpect(status().isCreated());
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a lead", "leadId", lead))
        .andExpect(status().isCreated());
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a deal", "dealId", deal))
        .andExpect(status().isCreated());

    // The database refuses it too.
    assertThatThrownBy(
            () -> jdbc.update("UPDATE activities SET company_id = NULL WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void companyTimelineIncludesContactAndDealActivities() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long initech = record(token, COMPANIES, w, Map.of("name", "Initech"));
    long other = record(token, COMPANIES, w, Map.of("name", "Other"));
    long peter = record(token, CONTACTS, w, Map.of("firstName", "Peter", "companyId", initech));
    long loner = record(token, CONTACTS, w, Map.of("firstName", "Loner"));
    long tps =
        record(token, DEALS, w, Map.of("name", "TPS reports", "amount", "1", "companyId", initech));
    long lead = record(token, LEADS, w, Map.of("firstName", "Ada"));

    log(token, w, "CALL", "On the contact", "2031-01-02T09:00:00Z", "contactId", peter);
    log(token, w, "MEETING", "On the deal", "2031-01-03T09:00:00Z", "dealId", tps);
    log(token, w, "NOTE", "On the company", "2031-01-01T09:00:00Z", "companyId", initech);
    log(token, w, "NOTE", "Someone else's", "2031-01-04T09:00:00Z", "companyId", other);
    log(token, w, "NOTE", "A contact without company", "2031-01-05T09:00:00Z", "contactId", loner);
    log(token, w, "NOTE", "On a lead", "2031-01-06T09:00:00Z", "leadId", lead);

    // The company's timeline: its own, its contact's and its deal's, newest first.
    list(token, w, "companyId", String.valueOf(initech))
        .andExpect(subjects("On the deal", "On the contact", "On the company"))
        .andExpect(jsonPath("$.totalElements").value(3));
    list(token, w, "contactId", String.valueOf(peter)).andExpect(subjects("On the contact"));
    list(token, w, "dealId", String.valueOf(tps)).andExpect(subjects("On the deal"));
    list(token, w, "leadId", String.valueOf(lead)).andExpect(subjects("On a lead"));
    list(token, w, "companyId", String.valueOf(other)).andExpect(subjects("Someone else's"));
    // Links combine: both must hold.
    list(token, w, "companyId", String.valueOf(initech), "dealId", String.valueOf(tps))
        .andExpect(subjects("On the deal"));
    list(token, w, "companyId", String.valueOf(other), "dealId", String.valueOf(tps))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void companyFillRules() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long a = record(token, COMPANIES, w, Map.of("name", "A"));
    long b = record(token, COMPANIES, w, Map.of("name", "B"));
    long contactA = record(token, CONTACTS, w, Map.of("firstName", "Ann", "companyId", a));
    long contactB = record(token, CONTACTS, w, Map.of("firstName", "Bob", "companyId", b));
    long noCompany = record(token, CONTACTS, w, Map.of("firstName", "Nobody"));
    long dealB = record(token, DEALS, w, Map.of("name", "DB", "amount", "1", "companyId", b));
    long lead = record(token, LEADS, w, Map.of("firstName", "Ada"));

    // The contact's company comes before the deal's; the deal's fills in for a contact without.
    Map<String, Object> both = activity("NOTE", "Both", "contactId", contactA);
    both.put("dealId", dealB);
    assertThat(companyOf(send(token, post(ACTIVITIES, w), both))).isEqualTo(a);
    Map<String, Object> bare = activity("NOTE", "Bare contact", "contactId", noCompany);
    bare.put("dealId", dealB);
    assertThat(companyOf(send(token, post(ACTIVITIES, w), bare))).isEqualTo(b);
    // A lead has no company of its own: nothing to fill in.
    send(token, post(ACTIVITIES, w), activity("NOTE", "Lead", "leadId", lead))
        .andExpect(jsonPath("$.company").doesNotExist());
    // A company the request names wins.
    Map<String, Object> named = activity("NOTE", "Named", "contactId", contactA);
    named.put("companyId", b);
    long id =
        id(send(token, post(ACTIVITIES, w), named).andExpect(jsonPath("$.company.id").value(b)));

    // An edit sending companyId null re-derives it from the contact it now names (A, then B)...
    Map<String, Object> edit = edit("NOTE", "Named", 0);
    edit.put("contactId", contactA);
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company.id").value(a))
        .andExpect(jsonPath("$.version").value(1));
    edit.put("version", 1);
    edit.put("contactId", contactB);
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(jsonPath("$.company.id").value(b))
        .andExpect(jsonPath("$.contact.id").value(contactB))
        .andExpect(jsonPath("$.version").value(2));
    // ...keeps the one it names, whatever the contact's...
    edit.put("version", 2);
    edit.put("contactId", contactA);
    edit.put("companyId", b);
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(jsonPath("$.company.id").value(b))
        .andExpect(jsonPath("$.contact.id").value(contactA));
    // ...and clears it when the links name none to take it from.
    edit.put("version", 3);
    edit.put("companyId", null);
    edit.put("contactId", noCompany);
    send(token, put(ACTIVITY, w, id), edit)
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact.id").value(noCompany));
  }

  @Test
  void authorEditsOwnRepCannotEditOthers() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    long company = record(ownerToken, COMPANIES, w, Map.of("name", "Initech"));
    Member r1 = member(w, WorkspaceRole.SALES_REP);
    Member r2 = member(w, WorkspaceRole.SALES_REP);
    Member manager = member(w, WorkspaceRole.MANAGER);

    long id = create(r1.token(), w, activity("CALL", "R1's call", company));
    send(r1.token(), get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.author.userId").value(r1.userId()));
    // The author edits; the author is the caller, whatever the request says.
    Map<String, Object> forged = activity("CALL", "Forged", company);
    forged.put("authorId", r2.userId());
    send(r1.token(), post(ACTIVITIES, w), forged)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.author.userId").value(r1.userId()));
    Map<String, Object> own = edit("CALL", "R1's call, edited", 0);
    own.put("companyId", company);
    send(r1.token(), put(ACTIVITY, w, id), own)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("R1's call, edited"))
        .andExpect(jsonPath("$.author.userId").value(r1.userId()));

    // Another rep reads, but can't change or delete: 403 before the version (stale or current)
    // and the links are checked; only bean validation of the body, in the controller, comes first.
    send(r2.token(), get(ACTIVITY, w, id)).andExpect(status().isOk());
    own.put("subject", "Taken");
    own.put("version", 0);
    send(r2.token(), put(ACTIVITY, w, id), own).andExpect(status().isForbidden());
    own.put("version", 1);
    send(r2.token(), put(ACTIVITY, w, id), own).andExpect(status().isForbidden());
    send(r2.token(), delete(ACTIVITY, w, id)).andExpect(status().isForbidden());
    send(r1.token(), get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.subject").value("R1's call, edited"))
        .andExpect(jsonPath("$.author.userId").value(r1.userId()))
        .andExpect(jsonPath("$.version").value(1));

    // A manager (RECORDS_EDIT_ANY) edits it, and the author stays the author.
    own.put("subject", "Manager's edit");
    send(manager.token(), put(ACTIVITY, w, id), own)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author.userId").value(r1.userId()));
    send(manager.token(), delete(ACTIVITY, w, id)).andExpect(status().isNoContent());

    // An author deletes their own.
    long mine = create(r2.token(), w, activity("NOTE", "R2's note", company));
    send(r2.token(), delete(ACTIVITY, w, mine)).andExpect(status().isNoContent());
    send(r2.token(), get(ACTIVITY, w, mine)).andExpect(status().isNotFound());
  }

  @Test
  void authorBecomesEmptyOnceTheyLeave() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    long company = record(ownerToken, COMPANIES, w, Map.of("name", "Initech"));
    Member author = member(w, WorkspaceRole.SALES_REP);
    Member bystander = member(w, WorkspaceRole.SALES_REP);
    Member manager = member(w, WorkspaceRole.MANAGER);
    long id = create(author.token(), w, activity("CALL", "Left behind", company));

    send(
            author.token(),
            delete("/api/v1/workspaces/{workspaceId}/members/{userId}", w, author.userId()))
        .andExpect(status().isNoContent());

    // The row stays, without an author (the foreign key cleared the column)...
    send(ownerToken, get(ACTIVITY, w, id))
        .andExpect(jsonPath("$.subject").value("Left behind"))
        .andExpect(jsonPath("$.author").doesNotExist());
    assertThat(jdbc.queryForObject("SELECT author_id FROM activities WHERE id = ?", Long.class, id))
        .isNull();
    // ...which only RECORDS_EDIT_ANY can still edit or delete.
    Map<String, Object> edit = edit("CALL", "Mine now", 0);
    edit.put("companyId", company);
    send(bystander.token(), put(ACTIVITY, w, id), edit).andExpect(status().isForbidden());
    send(bystander.token(), delete(ACTIVITY, w, id)).andExpect(status().isForbidden());
    send(manager.token(), put(ACTIVITY, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author").doesNotExist());
    send(manager.token(), delete(ACTIVITY, w, id)).andExpect(status().isNoContent());
  }

  @Test
  void deleteIsAudited() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));
    Map<String, Object> input = activity("MEETING", "Quarterly review", company);
    input.put("body", "Budget");
    input.put("occurredAt", "2031-06-01T09:00:00Z");
    long id = create(token, w, input);
    Map<String, Object> edit = edit("MEETING", "Quarterly review", 0);
    edit.put("body", "Budget and hiring");
    edit.put("occurredAt", "2031-06-01T09:00:00Z");
    edit.put("companyId", company);
    send(token, put(ACTIVITY, w, id), edit).andExpect(status().isOk());

    send(token, delete(ACTIVITY, w, id)).andExpect(status().isNoContent());
    send(token, get(ACTIVITY, w, id)).andExpect(status().isNotFound());
    send(token, delete(ACTIVITY, w, id)).andExpect(status().isNotFound());
    list(token, w).andExpect(jsonPath("$.totalElements").value(0));

    // One DELETE event, newest first, with the last state: what the activity was, field by
    // field, as the "from" of each change. The repeated delete wrote nothing.
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.DELETE, AuditAction.UPDATE, AuditAction.CREATE);
    AuditEvent deleted = trail.get(0);
    assertThat(deleted.getEntityLabel()).isEqualTo("Quarterly review");
    assertThat(deleted.getActorEmail()).isEqualTo(email);
    assertThat(deleted.getChanges())
        .containsOnlyKeys("id", "type", "subject", "body", "occurredAt", "author", "company");
    assertThat(deleted.getChanges().get("body")).isEqualTo(gone("Budget and hiring"));
    assertThat(deleted.getChanges().get("type")).isEqualTo(gone("MEETING"));
    assertThat(deleted.getChanges().get("occurredAt")).isEqualTo(gone("2031-06-01T09:00:00Z"));
  }

  @Test
  void filterByTypeAndPeriod() throws Exception {
    String ownerEmail = uniqueEmail();
    String token = tokenFor(ownerEmail);
    long w = createWorkspace(token, "Acme");
    Member rep = member(w, WorkspaceRole.SALES_REP);
    long company = record(token, COMPANIES, w, Map.of("name", "Initech"));

    log(token, w, "CALL", "call 1", "2031-01-10T09:00:00Z", "companyId", company);
    log(rep.token(), w, "EMAIL", "email 1", "2031-01-20T09:00:00Z", "companyId", company);
    log(token, w, "CALL", "call 2", "2031-01-20T09:00:00Z", "companyId", company);
    log(rep.token(), w, "MEETING", "meeting 1", "2031-01-30T09:00:00Z", "companyId", company);
    log(token, w, "NOTE", "note 1", "2031-02-10T09:00:00Z", "companyId", company);
    long ownerId = userId(ownerEmail);

    // Newest first; the same time lists the higher id (the later insert) first.
    list(token, w).andExpect(subjects("note 1", "meeting 1", "call 2", "email 1", "call 1"));
    list(token, w, "type", "CALL").andExpect(subjects("call 2", "call 1"));
    list(token, w, "type", "NOTE").andExpect(subjects("note 1"));
    list(token, w, "type", "SMS").andExpect(status().isBadRequest());
    list(token, w, "authorId", String.valueOf(rep.userId()))
        .andExpect(subjects("meeting 1", "email 1"));
    list(token, w, "authorId", String.valueOf(ownerId))
        .andExpect(subjects("note 1", "call 2", "call 1"));
    list(token, w, "type", "CALL", "authorId", String.valueOf(rep.userId()))
        .andExpect(jsonPath("$.totalElements").value(0));
    // From is inclusive, to is exclusive: a period can be chained without overlap.
    list(token, w, "from", "2031-01-20T09:00:00Z")
        .andExpect(subjects("note 1", "meeting 1", "call 2", "email 1"));
    list(token, w, "to", "2031-01-20T09:00:00Z").andExpect(subjects("call 1"));
    list(token, w, "from", "2031-01-20T09:00:00Z", "to", "2031-01-30T09:00:00Z")
        .andExpect(subjects("call 2", "email 1"));
    list(token, w, "from", "2031-01-20T09:00:00Z", "to", "2031-01-20T09:00:00Z")
        .andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "from", "2031-02-01T00:00:00Z", "to", "2031-01-01T00:00:00Z")
        .andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "type", "CALL", "from", "2031-01-15T00:00:00Z").andExpect(subjects("call 2"));
    list(token, w, "from", "soon").andExpect(status().isBadRequest());
    list(token, w, "to", "2031-01-20").andExpect(status().isBadRequest());
    // Paging keeps the order and the filters' total.
    list(token, w, "size", "2", "page", "1")
        .andExpect(subjects("call 2", "email 1"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3));
    list(token, w, "size", "2", "page", "2").andExpect(subjects("call 1"));
    list(token, w, "page", "-1").andExpect(status().isBadRequest());
    list(token, w, "size", "0").andExpect(status().isBadRequest());
    list(token, w, "size", "101").andExpect(status().isBadRequest());
  }

  @Test
  void linkFromAnotherWorkspaceIs400() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long ownCompany = record(a, COMPANIES, wa, Map.of("name", "A's"));
    long theirCompany = record(b, COMPANIES, wb, Map.of("name", "B's"));
    long theirContact = record(b, CONTACTS, wb, Map.of("firstName", "B"));
    long theirLead = record(b, LEADS, wb, Map.of("firstName", "B"));
    long theirDeal = record(b, DEALS, wb, Map.of("name", "B's", "amount", "1"));
    long own = create(a, wa, activity("CALL", "Mine", ownCompany));
    Map<String, Long> theirs =
        Map.of(
            "companyId", theirCompany,
            "contactId", theirContact,
            "leadId", theirLead,
            "dealId", theirDeal);

    for (Map.Entry<String, Long> link : theirs.entrySet()) {
      send(a, post(ACTIVITIES, wa), activity("CALL", "X", link.getKey(), link.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.fieldErrors." + link.getKey()).value("is not found in this workspace"));
      Map<String, Object> edit = edit("CALL", "Mine", 0);
      edit.put("companyId", ownCompany);
      edit.put(link.getKey(), link.getValue());
      send(a, put(ACTIVITY, wa, own), edit)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.fieldErrors." + link.getKey()).value("is not found in this workspace"));
      // An id that exists nowhere is the same 400.
      send(a, post(ACTIVITIES, wa), activity("CALL", "X", link.getKey(), Long.MAX_VALUE))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors." + link.getKey()).exists());
    }
    list(a, wa).andExpect(jsonPath("$.totalElements").value(1));
    send(a, get(ACTIVITY, wa, own))
        .andExpect(jsonPath("$.subject").value("Mine"))
        .andExpect(jsonPath("$.version").value(0));

    // The composite foreign keys are the backstop, for the author column too.
    Map<String, Long> columns =
        Map.of(
            "company_id", theirCompany,
            "contact_id", theirContact,
            "lead_id", theirLead,
            "deal_id", theirDeal,
            "author_id", userId(bEmail));
    for (Map.Entry<String, Long> column : columns.entrySet()) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE activities SET " + column.getKey() + " = ? WHERE id = ?",
                      column.getValue(),
                      own))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long company = record(a, COMPANIES, wa, Map.of("name", "A's"));
    long theirs = create(a, wa, activity("CALL", "A's", company));
    long bCompany = record(b, COMPANIES, wb, Map.of("name", "B's"));

    // B's token and workspace, A's activity id.
    send(b, get(ACTIVITY, wb, theirs)).andExpect(status().isNotFound());
    Map<String, Object> edit = edit("CALL", "Mine now", 0);
    edit.put("companyId", bCompany);
    send(b, put(ACTIVITY, wb, theirs), edit).andExpect(status().isNotFound());
    send(b, delete(ACTIVITY, wb, theirs)).andExpect(status().isNotFound());
    list(b, wb).andExpect(jsonPath("$.totalElements").value(0));
    list(b, wb, "companyId", String.valueOf(company))
        .andExpect(jsonPath("$.totalElements").value(0));
    // B has no access to A's workspace at all.
    send(b, get(ACTIVITY, wa, theirs)).andExpect(status().isNotFound());
    send(b, delete(ACTIVITY, wa, theirs)).andExpect(status().isNotFound());
    list(b, wa).andExpect(status().isNotFound());
    send(a, get(ACTIVITY, wa, Long.MAX_VALUE)).andExpect(status().isNotFound());
    send(a, get(ACTIVITY, wb, theirs)).andExpect(status().isNotFound());
    send(a, get(ACTIVITY, wa, theirs))
        .andExpect(jsonPath("$.subject").value("A's"))
        .andExpect(jsonPath("$.version").value(0));
    assertThat(trail(wa, theirs)).hasSize(1);
  }

  /** Archiving hides a record, it doesn't end its history: it can still be logged against. */
  @Test
  void archivedRecordsCanBeLinked() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long company = record(token, COMPANIES, w, Map.of("name", "Old"));
    long contact = record(token, CONTACTS, w, Map.of("firstName", "Ann", "companyId", company));
    long deal = record(token, DEALS, w, Map.of("name", "Done", "amount", "1"));
    long lead = record(token, LEADS, w, Map.of("firstName", "Ada"));
    for (String[] archived :
        new String[][] {
          {COMPANIES, String.valueOf(company)},
          {CONTACTS, String.valueOf(contact)},
          {DEALS, String.valueOf(deal)},
          {LEADS, String.valueOf(lead)}
        }) {
      send(token, post(archived[0] + "/" + archived[1] + "/archive", w)).andExpect(status().isOk());
    }

    send(token, post(ACTIVITIES, w), activity("CALL", "Late call", "contactId", contact))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.company.id").value(company));
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a deal", "dealId", deal))
        .andExpect(status().isCreated());
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a lead", "leadId", lead))
        .andExpect(status().isCreated());
    send(token, post(ACTIVITIES, w), activity("NOTE", "On a company", "companyId", company))
        .andExpect(status().isCreated());
    list(token, w).andExpect(jsonPath("$.totalElements").value(4));
  }

  /** The change of a field that existed and is gone: what it was, and null. */
  private static Map<String, Object> gone(Object from) {
    Map<String, Object> change = new HashMap<>();
    change.put("from", from);
    change.put("to", null);
    return change;
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

  /** The id of a record created at {@code path} (a collection URL template). */
  private long record(String token, String path, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(path, w), input));
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(ACTIVITIES, w), input));
  }

  /** Logs an activity at {@code occurredAt} against one link. */
  private long log(
      String token, long w, String type, String subject, String occurredAt, String link, long id)
      throws Exception {
    Map<String, Object> input = activity(type, subject, link, id);
    input.put("occurredAt", occurredAt);
    return create(token, w, input);
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
  }

  private long companyOf(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("company").get("id").asLong();
  }

  /** A create body with a company link ({@code type} and {@code subject} may be null: absent). */
  private static Map<String, Object> activity(String type, String subject, long companyId) {
    return activity(type, subject, "companyId", companyId);
  }

  private static Map<String, Object> activity(
      String type, String subject, String link, long linkId) {
    Map<String, Object> body = new HashMap<>();
    if (type != null) body.put("type", type);
    if (subject != null) body.put("subject", subject);
    body.put(link, linkId);
    return body;
  }

  /** A PUT body without links: the type, subject, version and any other fields are added. */
  private static Map<String, Object> edit(String type, String subject, long version) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    body.put("subject", subject);
    body.put("version", version);
    return body;
  }

  private void malformed(String token, long w, Map<String, ?> input) throws Exception {
    send(token, post(ACTIVITIES, w), input).andExpect(status().isBadRequest());
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(ACTIVITIES, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(ACTIVITIES, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  private static ResultMatcher subjects(String... subjects) {
    return jsonPath("$.content[*].subject", contains(subjects));
  }

  /** The activity's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.ACTIVITY && e.getEntityId() == id)
        .toList();
  }
}
