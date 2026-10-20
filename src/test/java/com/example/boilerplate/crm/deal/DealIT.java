package com.example.boilerplate.crm.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
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
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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

class DealIT extends WorkspaceTestSupport {
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";
  static final String DEAL = DEALS + "/{id}";
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String SOURCES = "/api/v1/workspaces/{workspaceId}/lead-sources";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";
  static final String CONTACTS = "/api/v1/workspaces/{workspaceId}/contacts";
  static final String LEADS = "/api/v1/workspaces/{workspaceId}/leads";

  @Autowired AuditEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired DealStageChangeRepository history;

  @Test
  void createDefaultsToFirstOpenStageAndWorkspaceCurrency() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);

    long id =
        body(send(token, post(DEALS, w), Map.of("name", " Big deal　", "amount", "1000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Big deal"))
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.stage.id").value(stage.get("Qualification")))
                .andExpect(jsonPath("$.stage.name").value("Qualification"))
                .andExpect(jsonPath("$.stage.kind").value("OPEN"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.probability").value(10))
                .andExpect(jsonPath("$.probabilityOverride").doesNotExist())
                .andExpect(jsonPath("$.expectedCloseDate").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.lostReason").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.source").doesNotExist())
                .andExpect(jsonPath("$.lead").doesNotExist())
                .andExpect(jsonPath("$.owner.userId").value(userId(email)))
                .andExpect(jsonPath("$.owner.email").value(email))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();

    // Every create writes the first history row: no stage before, by the caller.
    send(token, get(DEAL + "/history", w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].fromStage").doesNotExist())
        .andExpect(jsonPath("$[0].toStage.id").value(stage.get("Qualification")))
        .andExpect(jsonPath("$[0].toStage.name").value("Qualification"))
        .andExpect(jsonPath("$[0].changedBy.userId").value(userId(email)))
        .andExpect(jsonPath("$[0].changedAt").exists());
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Big deal");
    assertThat(trail.get(0).getChanges()).isEmpty();

    // The default is the open stage with the lowest position, not the lowest id.
    send(
            token,
            put(STAGES + "/order", w),
            Map.of(
                "stageIds",
                List.of(
                    stage.get("Proposal"),
                    stage.get("Qualification"),
                    stage.get("Needs analysis"),
                    stage.get("Negotiation"))))
        .andExpect(status().isOk());
    send(token, post(DEALS, w), Map.of("name", "Second", "amount", "1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stage.name").value("Proposal"))
        .andExpect(jsonPath("$.probability").value(50));
  }

  @Test
  void createGetUpdate() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long company = id(send(token, post(COMPANIES, w), Map.of("name", "Initech")));
    long contact =
        id(send(token, post(CONTACTS, w), Map.of("firstName", "Peter", "lastName", "Gibbons")));
    JsonNode source = firstSource(token, w);
    Map<String, Object> input = new HashMap<>();
    input.put("name", "TPS reports");
    input.put("amount", "1250.10");
    input.put("currency", "usd");
    input.put("stageId", stage.get("Proposal"));
    input.put("probability", 35);
    input.put("expectedCloseDate", "2026-12-01");
    input.put("companyId", company);
    input.put("contactId", contact);
    input.put("sourceId", source.get("id").asLong());
    input.put("notes", " Renewal ");

    long id =
        body(send(token, post(DEALS, w), input)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("1250.10"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.stage.name").value("Proposal"))
                .andExpect(jsonPath("$.probability").value(35))
                .andExpect(jsonPath("$.probabilityOverride").value(35))
                .andExpect(jsonPath("$.expectedCloseDate").value("2026-12-01"))
                .andExpect(jsonPath("$.company.id").value(company))
                .andExpect(jsonPath("$.company.name").value("Initech"))
                .andExpect(jsonPath("$.contact.id").value(contact))
                .andExpect(jsonPath("$.contact.fullName").value("Peter Gibbons"))
                .andExpect(jsonPath("$.source.name").value(source.get("name").asText()))
                .andExpect(jsonPath("$.notes").value("Renewal")))
            .get("id")
            .asLong();

    send(token, get(DEAL, w, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("TPS reports"))
        .andExpect(jsonPath("$.contact.fullName").value("Peter Gibbons"))
        .andExpect(jsonPath("$.source.id").value(source.get("id").asLong()))
        .andExpect(jsonPath("$.version").value(0));
    // The list shows summaries: no notes, source or override.
    list(token, w)
        .andExpect(jsonPath("$.content[0].name").value("TPS reports"))
        .andExpect(jsonPath("$.content[0].amount").value("1250.10"))
        .andExpect(jsonPath("$.content[0].stage.kind").value("OPEN"))
        .andExpect(jsonPath("$.content[0].status").value("OPEN"))
        .andExpect(jsonPath("$.content[0].probability").value(35))
        .andExpect(jsonPath("$.content[0].company.name").value("Initech"))
        .andExpect(jsonPath("$.content[0].contact.fullName").value("Peter Gibbons"))
        .andExpect(jsonPath("$.content[0].owner.email").value(email))
        .andExpect(jsonPath("$.content[0].version").value(0))
        .andExpect(jsonPath("$.content[0].notes").doesNotExist())
        .andExpect(jsonPath("$.content[0].source").doesNotExist())
        .andExpect(jsonPath("$.content[0].probabilityOverride").doesNotExist());

    // Omitted optional fields are cleared (the probability falls back to the stage's, the currency
    // to the workspace's); a PUT has no stage, so one sent along changes nothing.
    Map<String, Object> edit =
        edit("TPS reports", "1250.1", userId(email), 0, "stageId", stage.get("Won"));
    send(token, put(DEAL, w, id), edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value("1250.10"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.stage.name").value("Proposal"))
        .andExpect(jsonPath("$.probability").value(50))
        .andExpect(jsonPath("$.probabilityOverride").doesNotExist())
        .andExpect(jsonPath("$.expectedCloseDate").doesNotExist())
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.contact").doesNotExist())
        .andExpect(jsonPath("$.source").doesNotExist())
        .andExpect(jsonPath("$.notes").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
    // The same values again change nothing: same version, no audit event.
    edit.put("version", 1);
    send(token, put(DEAL, w, id), edit).andExpect(jsonPath("$.version").value(1));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    // The deal's own probability (probabilityOverride), not the effective one that follows it.
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys(
            "currency",
            "probabilityOverride",
            "expectedCloseDate",
            "company",
            "contact",
            "source",
            "notes");
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("TPS reports");
    // No history row for an edit.
    send(token, get(DEAL + "/history", w, id)).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void validation() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    invalid(token, w, Map.of("amount", "1"), "name");
    invalid(token, w, Map.of("name", "　 ", "amount", "1"), "name");
    invalid(token, w, Map.of("name", "x".repeat(201), "amount", "1"), "name");
    invalid(token, w, Map.of("name", "x"), "amount");
    invalid(token, w, Map.of("name", "x", "amount", "1", "probability", 101), "probability");
    invalid(token, w, Map.of("name", "x", "amount", "1", "probability", -1), "probability");
    invalid(token, w, Map.of("name", "x", "amount", "1", "notes", "x".repeat(5001)), "notes");
    invalid(token, w, Map.of("name", "x", "amount", "1", "currency", "ZZZ"), "currency");
    invalid(token, w, Map.of("name", "x", "amount", "1", "currency", "EURO"), "currency");
    malformed(token, w, Map.of("name", "x", "amount", "1", "expectedCloseDate", "2026-02-30"));
    malformed(token, w, Map.of("name", "x", "amount", "1", "stageId", "first"));

    long id = create(token, w, Map.of("name", "Deal", "amount", "1"));
    send(token, put(DEAL, w, id), Map.of("name", "Deal", "amount", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    send(token, post(DEAL + "/move", w, id), Map.of("version", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.stageId").exists());
    long proposal = stages(token, w).get("Proposal");
    send(token, post(DEAL + "/move", w, id), Map.of("stageId", proposal))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());
    move(token, w, id, stages(token, w).get("Lost"), "x".repeat(501), 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lostReason").exists());
    list(token, w).andExpect(jsonPath("$.totalElements").value(1));
    send(token, get(DEAL, w, id)).andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void amountIsAStringAndKeepsCents() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");

    long id =
        body(send(token, post(DEALS, w), Map.of("name", "A", "amount", "1250.10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("1250.10")))
            .get("id")
            .asLong();
    send(token, get(DEAL, w, id)).andExpect(jsonPath("$.amount").value("1250.10"));
    send(token, post(DEALS, w), Map.of("name", "B", "amount", 99.5))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value("99.50"));
    send(token, post(DEALS, w), Map.of("name", "C", "amount", "0"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value("0.00"));
    send(token, post(DEALS, w), Map.of("name", "D", "amount", "9999999999999.99"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value("9999999999999.99"));

    invalid(token, w, Map.of("name", "x", "amount", 1250.105), "amount");
    invalid(token, w, Map.of("name", "x", "amount", "1250.105"), "amount");
    invalid(token, w, Map.of("name", "x", "amount", -1), "amount");
    invalid(token, w, Map.of("name", "x", "amount", "-0.01"), "amount");
    invalid(token, w, Map.of("name", "x", "amount", "10000000000000"), "amount");
    malformed(token, w, Map.of("name", "x", "amount", "lots"));

    // 1250.1 is 1250.10: no phantom change, no audit event.
    send(token, put(DEAL, w, id), edit("A", 1250.1, userId(email), 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value("1250.10"))
        .andExpect(jsonPath("$.version").value(0));
    assertThat(trail(w, id)).extracting(AuditEvent::getAction).containsExactly(AuditAction.CREATE);
    send(token, put(DEAL, w, id), edit("A", "1250.11", userId(email), 0))
        .andExpect(jsonPath("$.amount").value("1250.11"))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(trail(w, id).get(0).getChanges())
        .containsOnlyKeys("amount")
        .containsEntry("amount", Map.of("from", "1250.10", "to", "1250.11"));
  }

  @Test
  void moveThroughStagesWritesHistory() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long id = create(token, w, Map.of("name", "Deal", "amount", "10"));

    move(token, w, id, stage.get("Proposal"), null, 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage.name").value("Proposal"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.probability").value(50))
        .andExpect(jsonPath("$.closedAt").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
    // A reason sent with another stage than Lost is dropped.
    move(token, w, id, stage.get("Won"), "ignored", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage.name").value("Won"))
        .andExpect(jsonPath("$.stage.kind").value("WON"))
        .andExpect(jsonPath("$.status").value("WON"))
        .andExpect(jsonPath("$.probability").value(100))
        .andExpect(jsonPath("$.closedAt").exists())
        .andExpect(jsonPath("$.lostReason").doesNotExist())
        .andExpect(jsonPath("$.version").value(2));
    send(token, get(DEAL, w, id))
        .andExpect(jsonPath("$.status").value("WON"))
        .andExpect(jsonPath("$.closedAt").exists());

    send(token, get(DEAL + "/history", w, id))
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].toStage.name", contains("Won", "Proposal", "Qualification")))
        .andExpect(jsonPath("$[0].fromStage.name").value("Proposal"))
        .andExpect(jsonPath("$[1].fromStage.name").value("Qualification"))
        .andExpect(jsonPath("$[2].fromStage").doesNotExist())
        .andExpect(jsonPath("$[0].changedBy.email").value(email));

    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.STAGE_CHANGE, AuditAction.STAGE_CHANGE, AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsEntry("stage", Map.of("from", "Proposal", "to", "Won"))
        .containsEntry("status", Map.of("from", "OPEN", "to", "WON"))
        .containsKey("closedAt");
    // The probability that only follows the stage, and the time it was entered, are left out.
    assertThat(trail.get(1).getChanges())
        .containsOnlyKeys("stage")
        .containsEntry("stage", Map.of("from", "Qualification", "to", "Proposal"));
    assertThat(trail.get(0).getEntityLabel()).isEqualTo("Deal");

    // An override stays through the moves.
    long fixed = create(token, w, Map.of("name", "Fixed", "amount", "1", "probability", 30));
    move(token, w, fixed, stage.get("Negotiation"), null, 0)
        .andExpect(jsonPath("$.probability").value(30))
        .andExpect(jsonPath("$.probabilityOverride").value(30));
  }

  /**
   * When the deal entered its current stage: the time of its newest history entry, on the deal, the
   * list's rows and the board's cards. A new lost reason is no move: the time stays.
   */
  @Test
  void stageEnteredAtIsTheNewestHistoryEntry() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long id = create(token, w, Map.of("name", "Moved", "amount", "10"));
    long stays = create(token, w, Map.of("name", "Stays", "amount", "1"));
    String created = newestChange(token, w, id);
    send(token, get(DEAL, w, id)).andExpect(jsonPath("$.stageEnteredAt").value(created));

    String moved =
        body(move(token, w, id, stage.get("Lost"), "Too expensive", 0).andExpect(status().isOk()))
            .get("stageEnteredAt")
            .asText();
    assertThat(moved).isEqualTo(newestChange(token, w, id)).isNotEqualTo(created);
    move(token, w, id, stage.get("Lost"), "Went with a rival", 1)
        .andExpect(jsonPath("$.lostReason").value("Went with a rival"))
        .andExpect(jsonPath("$.stageEnteredAt").value(moved));
    send(token, get(DEAL, w, id)).andExpect(jsonPath("$.stageEnteredAt").value(moved));

    list(token, w, "sort", "NAME")
        .andExpect(names("Moved", "Stays"))
        .andExpect(
            jsonPath(
                "$.content[*].stageEnteredAt", contains(moved, newestChange(token, w, stays))));
    JsonNode board = body(send(token, get(DEALS + "/board", w)));
    Map<String, String> cards = new HashMap<>();
    for (JsonNode column : board.get("stages")) {
      for (JsonNode card : column.get("deals")) {
        cards.put(card.get("name").asText(), card.get("stageEnteredAt").asText());
      }
    }
    assertThat(cards).isEqualTo(Map.of("Moved", moved, "Stays", newestChange(token, w, stays)));
  }

  /** The page's query goes through the tenant filter: another workspace's history is never read. */
  @Test
  void stageEnteredAtReadsOnlyTheWorkspacesHistory() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    long theirs = create(a, wa, Map.of("name", "Theirs", "amount", "1"));
    String bEmail = uniqueEmail();
    String b = tokenFor(bEmail);
    long wb = createWorkspace(b, "B");
    long own = create(b, wb, Map.of("name", "Own", "amount", "1"));
    Membership inB = new Membership(wb, userId(bEmail), bEmail, WorkspaceRole.OWNER);

    List<Object[]> rows =
        WorkspaceContext.runAs(inB, () -> history.stageEnteredAt(List.of(theirs, own)));

    assertThat(rows).extracting(row -> row[0]).containsExactly(own);
  }

  @Test
  void lostNeedsReasonAndReopenClears() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long id = create(token, w, Map.of("name", "Deal", "amount", "10"));

    move(token, w, id, stage.get("Lost"), null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lostReason").exists());
    move(token, w, id, stage.get("Lost"), "　 ", 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lostReason").exists());
    String lostAt =
        body(move(token, w, id, stage.get("Lost"), "  Too expensive　", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReason").value("Too expensive"))
                .andExpect(jsonPath("$.probability").value(0))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.version").value(1)))
            .get("closedAt")
            .asText();

    // The same Lost stage with a different reason saves the reason: a new version, an audit event
    // with only the reason, no history row, the close time kept.
    move(token, w, id, stage.get("Lost"), "Went with a rival", 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lostReason").value("Went with a rival"))
        .andExpect(jsonPath("$.closedAt").value(lostAt))
        .andExpect(jsonPath("$.version").value(2));
    List<AuditEvent> trail = trail(w, id);
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.STAGE_CHANGE, AuditAction.STAGE_CHANGE, AuditAction.CREATE);
    assertThat(trail.get(0).getChanges())
        .containsOnlyKeys("lostReason")
        .containsEntry("lostReason", Map.of("from", "Too expensive", "to", "Went with a rival"));
    send(token, get(DEAL + "/history", w, id)).andExpect(jsonPath("$.length()").value(2));

    // Every other same-stage move is a no-op: the reason as it is (stripped), none, a blank one.
    for (String reason : new String[] {"Went with a rival", " Went with a rival ", null, "　 "}) {
      move(token, w, id, stage.get("Lost"), reason, 2)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lostReason").value("Went with a rival"))
          .andExpect(jsonPath("$.version").value(2));
    }
    assertThat(trail(w, id)).hasSize(3);

    // Back to an open stage: reopened, without close time or reason.
    move(token, w, id, stage.get("Negotiation"), "ignored", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.closedAt").doesNotExist())
        .andExpect(jsonPath("$.lostReason").doesNotExist())
        .andExpect(jsonPath("$.version").value(3));
    send(token, get(DEAL, w, id))
        .andExpect(jsonPath("$.closedAt").doesNotExist())
        .andExpect(jsonPath("$.lostReason").doesNotExist());

    // Lost to Won is a new close and drops the reason.
    String lostAgain =
        body(move(token, w, id, stage.get("Lost"), "Budget", 3)).get("closedAt").asText();
    String wonAt =
        body(move(token, w, id, stage.get("Won"), null, 4)
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.lostReason").doesNotExist()))
            .get("closedAt")
            .asText();
    assertThat(Instant.parse(wonAt)).isAfter(Instant.parse(lostAgain));

    // Won to Lost needs a reason too, and is a new close.
    move(token, w, id, stage.get("Lost"), null, 5)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lostReason").exists());
    String lostAfterWin =
        body(move(token, w, id, stage.get("Lost"), "Changed their mind", 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReason").value("Changed their mind"))
                .andExpect(jsonPath("$.version").value(6)))
            .get("closedAt")
            .asText();
    assertThat(Instant.parse(lostAfterWin)).isAfter(Instant.parse(wonAt));
    // The close time a move answers is the one stored (microseconds), not a finer one.
    send(token, get(DEAL, w, id)).andExpect(jsonPath("$.closedAt").value(lostAfterWin));
    send(token, get(DEAL + "/history", w, id))
        .andExpect(
            jsonPath(
                "$[*].toStage.name",
                contains("Lost", "Won", "Lost", "Negotiation", "Lost", "Qualification")));

    // The same open stage again changes nothing either.
    long other = create(token, w, Map.of("name", "Other", "amount", "1"));
    move(token, w, other, stage.get("Qualification"), null, 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    send(token, get(DEAL + "/history", w, other)).andExpect(jsonPath("$.length()").value(1));
    assertThat(trail(w, other)).hasSize(1);
  }

  @Test
  void staleMoveIs409AndWritesNothing() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long id = create(token, w, Map.of("name", "Deal", "amount", "10"));
    move(token, w, id, stage.get("Proposal"), null, 0).andExpect(status().isOk());

    move(token, w, id, stage.get("Negotiation"), null, 0).andExpect(status().isConflict());
    // The rules come before the version: a stale client still learns what is wrong with its move.
    move(token, w, id, stage.get("Lost"), null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lostReason").exists());
    move(token, w, id, Long.MAX_VALUE, null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.stageId").value("is not found in this workspace"));
    // The current stage with a stale version is a conflict too.
    move(token, w, id, stage.get("Proposal"), null, 0).andExpect(status().isConflict());

    send(token, get(DEAL, w, id))
        .andExpect(jsonPath("$.stage.name").value("Proposal"))
        .andExpect(jsonPath("$.version").value(1));
    send(token, get(DEAL + "/history", w, id)).andExpect(jsonPath("$.length()").value(2));
    assertThat(trail(w, id))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.STAGE_CHANGE, AuditAction.CREATE);
  }

  @Test
  void stageOfAnotherWorkspaceIs400() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long theirs = stages(b, wb).get("Proposal");

    send(a, post(DEALS, wa), Map.of("name", "Deal", "amount", "1", "stageId", theirs))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.stageId").value("is not found in this workspace"));
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));

    long own = create(a, wa, Map.of("name", "Deal", "amount", "1"));
    move(a, wa, own, theirs, null, 0)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.stageId").value("is not found in this workspace"));
    send(a, get(DEAL, wa, own))
        .andExpect(jsonPath("$.stage.name").value("Qualification"))
        .andExpect(jsonPath("$.version").value(0));
    send(a, get(DEAL + "/history", wa, own)).andExpect(jsonPath("$.length()").value(1));
    // The composite foreign key is the backstop.
    assertThatThrownBy(() -> jdbc.update("UPDATE deals SET stage_id = ? WHERE id = ?", theirs, own))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void linksOfAnotherWorkspaceAre400() throws Exception {
    String email = uniqueEmail();
    String a = tokenFor(email);
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    String outsider = uniqueEmail();
    tokenFor(outsider);
    long theirCompany = id(send(b, post(COMPANIES, wb), Map.of("name", "B's")));
    long theirContact = id(send(b, post(CONTACTS, wb), Map.of("firstName", "B")));
    long theirSource = firstSource(b, wb).get("id").asLong();
    long theirLead = id(send(b, post(LEADS, wb), Map.of("firstName", "B")));
    long theirDeal = id(send(b, post(DEALS, wb), Map.of("name", "B's", "amount", "1")));
    long own = create(a, wa, Map.of("name", "Deal", "amount", "1"));
    Map<String, Long> theirs =
        Map.of("companyId", theirCompany, "contactId", theirContact, "sourceId", theirSource);

    for (Map.Entry<String, Long> link : theirs.entrySet()) {
      send(a, post(DEALS, wa), Map.of("name", "X", "amount", "1", link.getKey(), link.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.fieldErrors." + link.getKey()).value("is not found in this workspace"));
      send(
              a,
              put(DEAL, wa, own),
              edit("Deal", "1", userId(email), 0, link.getKey(), link.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors." + link.getKey()).exists());
    }
    send(a, post(DEALS, wa), Map.of("name", "X", "amount", "1", "ownerId", userId(outsider)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    send(a, put(DEAL, wa, own), edit("Deal", "1", userId(outsider), 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(1));
    send(a, get(DEAL, wa, own))
        .andExpect(jsonPath("$.company").doesNotExist())
        .andExpect(jsonPath("$.version").value(0));

    // The composite foreign keys are the backstop, the lead's converted deal included.
    for (String column : List.of("company_id", "contact_id", "source_id", "lead_id")) {
      long value =
          switch (column) {
            case "company_id" -> theirCompany;
            case "contact_id" -> theirContact;
            case "source_id" -> theirSource;
            default -> theirLead;
          };
      assertThatThrownBy(
              () -> jdbc.update("UPDATE deals SET " + column + " = ? WHERE id = ?", value, own))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    long ownLead = id(send(a, post(LEADS, wa), Map.of("firstName", "Ada", "lastName", "L")));
    long ownContact = id(send(a, post(CONTACTS, wa), Map.of("firstName", "Ada")));
    // Converted as a conversion writes it (the status columns go together), so only the foreign
    // key can refuse the other workspace's deal.
    jdbc.update(
        "UPDATE leads SET status = 'CONVERTED', converted_at = now(), converted_contact_id = ?"
            + " WHERE id = ?",
        ownContact,
        ownLead);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE leads SET converted_deal_id = ? WHERE id = ?", theirDeal, ownLead))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("leads_workspace_id_converted_deal_id_fkey");
    jdbc.update("UPDATE leads SET converted_deal_id = ? WHERE id = ?", own, ownLead);

    // One deal per lead; the deal shows its lead.
    jdbc.update("UPDATE deals SET lead_id = ? WHERE id = ?", ownLead, own);
    long second = create(a, wa, Map.of("name", "Second", "amount", "1"));
    assertThatThrownBy(
            () -> jdbc.update("UPDATE deals SET lead_id = ? WHERE id = ?", ownLead, second))
        .isInstanceOf(DataIntegrityViolationException.class);
    send(a, get(DEAL, wa, own))
        .andExpect(jsonPath("$.lead.id").value(ownLead))
        .andExpect(jsonPath("$.lead.fullName").value("Ada L"));
  }

  @Test
  void createInWonStageIs400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);

    for (String closed : List.of("Won", "Lost")) {
      send(
              token,
              post(DEALS, w),
              Map.of(
                  "name", "Deal", "amount", "1", "stageId", stage.get(closed), "lostReason", "x"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors.stageId").value("must be an open stage"));
    }
    list(token, w).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void repMovesOnlyOwn() throws Exception {
    String ownerToken = tokenFor(uniqueEmail());
    long w = createWorkspace(ownerToken, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    String managerEmail = uniqueEmail();
    String manager = tokenFor(managerEmail);
    addMember(w, managerEmail, WorkspaceRole.MANAGER);
    long repId = userId(repEmail);
    long managerId = userId(managerEmail);
    Map<String, Long> stage = stages(ownerToken, w);

    long own =
        body(send(rep, post(DEALS, w), Map.of("name", "Rep's", "amount", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.userId").value(repId)))
            .get("id")
            .asLong();
    move(rep, w, own, stage.get("Proposal"), null, 0).andExpect(status().isOk());
    send(rep, put(DEAL, w, own), edit("Rep's own", "1", repId, 1)).andExpect(status().isOk());

    long managers = create(manager, w, Map.of("name", "Manager's", "amount", "1"));
    send(rep, get(DEAL, w, managers)).andExpect(status().isOk());
    send(rep, get(DEAL + "/history", w, managers)).andExpect(status().isOk());
    move(rep, w, managers, stage.get("Proposal"), null, 0).andExpect(status().isForbidden());
    move(rep, w, managers, stage.get("Lost"), "Mine now", 0).andExpect(status().isForbidden());
    send(rep, put(DEAL, w, managers), edit("Taken", "1", managerId, 0))
        .andExpect(status().isForbidden());
    send(rep, post(DEAL + "/archive", w, managers)).andExpect(status().isForbidden());
    send(rep, post(DEAL + "/restore", w, managers)).andExpect(status().isForbidden());
    send(rep, post(DEALS, w), Map.of("name", "Gift", "amount", "1", "ownerId", managerId))
        .andExpect(status().isForbidden());
    send(manager, get(DEAL, w, managers))
        .andExpect(jsonPath("$.stage.name").value("Qualification"))
        .andExpect(jsonPath("$.version").value(0));
    send(manager, get(DEAL + "/history", w, managers)).andExpect(jsonPath("$.length()").value(1));

    // A manager moves anyone's deal; unassigned, it is a manager's to move, not a rep's.
    move(manager, w, own, stage.get("Negotiation"), null, 2).andExpect(status().isOk());
    send(manager, put(DEAL, w, own), edit("Rep's own", "1", null, 3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner").doesNotExist());
    move(rep, w, own, stage.get("Proposal"), null, 4).andExpect(status().isForbidden());
    move(manager, w, own, stage.get("Proposal"), null, 4).andExpect(status().isOk());
  }

  @Test
  void filters() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    Map<String, Long> stage = stages(token, w);
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme Corp")));
    long globex = id(send(token, post(COMPANIES, w), Map.of("name", "Globex")));
    long contact = id(send(token, post(CONTACTS, w), Map.of("firstName", "Hank")));

    create(
        token,
        w,
        Map.of(
            "name", "Alpha",
            "amount", "1",
            "companyId", acme,
            "contactId", contact,
            "expectedCloseDate", "2026-11-01"));
    create(
        rep,
        w,
        Map.of(
            "name", "Beta", "amount", "1", "companyId", globex, "expectedCloseDate", "2026-11-15"));
    long gamma =
        create(token, w, Map.of("name", "Gamma", "amount", "1", "expectedCloseDate", "2026-12-01"));
    long delta = create(token, w, Map.of("name", "Delta", "amount", "1"));
    long archived = create(token, w, Map.of("name", "Archived", "amount", "1", "companyId", acme));
    move(token, w, gamma, stage.get("Won"), null, 0).andExpect(status().isOk());
    move(token, w, delta, stage.get("Lost"), "No budget", 0).andExpect(status().isOk());
    send(token, post(DEAL + "/archive", w, archived)).andExpect(status().isOk());

    list(token, w, "status", "WON").andExpect(names("Gamma"));
    list(token, w, "status", "LOST").andExpect(names("Delta"));
    list(token, w, "status", "OPEN").andExpect(namesAnyOrder("Alpha", "Beta"));
    list(token, w, "status", "SOLD").andExpect(status().isBadRequest());
    list(token, w, "stageId", String.valueOf(stage.get("Qualification")))
        .andExpect(namesAnyOrder("Alpha", "Beta"));
    list(token, w, "stageId", String.valueOf(stage.get("Won"))).andExpect(names("Gamma"));
    list(token, w, "ownerId", String.valueOf(userId(repEmail))).andExpect(names("Beta"));
    list(token, w, "companyId", String.valueOf(acme)).andExpect(names("Alpha"));
    list(token, w, "companyId", String.valueOf(acme), "archived", "true")
        .andExpect(names("Archived"));
    list(token, w, "contactId", String.valueOf(contact)).andExpect(names("Alpha"));
    // Inclusive at both ends; deals without a date never match.
    list(token, w, "closeFrom", "2026-11-15").andExpect(namesAnyOrder("Beta", "Gamma"));
    list(token, w, "closeTo", "2026-11-15").andExpect(namesAnyOrder("Alpha", "Beta"));
    list(token, w, "closeFrom", "2026-11-15", "closeTo", "2026-11-15").andExpect(names("Beta"));
    list(token, w, "closeFrom", "2026-11-02", "closeTo", "2026-11-30").andExpect(names("Beta"));
    list(token, w, "closeFrom", "soon").andExpect(status().isBadRequest());
    // Name or company name, any case, literally.
    list(token, w, "q", "ALP").andExpect(names("Alpha"));
    list(token, w, "q", "acme").andExpect(names("Alpha"));
    list(token, w, "q", "glob").andExpect(names("Beta"));
    list(token, w, "q", "%").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", "_").andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "q", " ").andExpect(jsonPath("$.totalElements").value(4));
    list(token, w, "q", "x".repeat(201)).andExpect(status().isBadRequest());
    list(token, w, "status", "OPEN", "q", "beta", "ownerId", String.valueOf(userId(repEmail)))
        .andExpect(names("Beta"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  /** {@code sourceId}: the deals from that source; another workspace's source matches nothing. */
  @Test
  void filterBySource() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long web = id(send(token, post(SOURCES, w), Map.of("name", "Web")));
    long fair = id(send(token, post(SOURCES, w), Map.of("name", "Fair")));
    create(token, w, Map.of("name", "From web", "amount", "1", "sourceId", web));
    create(token, w, Map.of("name", "From fair", "amount", "1", "sourceId", fair));
    create(token, w, Map.of("name", "No source", "amount", "1"));
    long archived = create(token, w, Map.of("name", "Old web", "amount", "1", "sourceId", web));
    send(token, post(DEAL + "/archive", w, archived)).andExpect(status().isOk());
    String other = tokenFor(uniqueEmail());
    long w2 = createWorkspace(other, "Other");
    long theirs = id(send(other, post(SOURCES, w2), Map.of("name", "Theirs")));
    create(other, w2, Map.of("name", "Their deal", "amount", "1", "sourceId", theirs));

    list(token, w, "sourceId", String.valueOf(web)).andExpect(names("From web"));
    list(token, w, "sourceId", String.valueOf(fair)).andExpect(names("From fair"));
    list(token, w, "sourceId", String.valueOf(web), "archived", "true").andExpect(names("Old web"));
    list(token, w, "sourceId", String.valueOf(theirs))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    list(token, w, "sourceId", "web").andExpect(status().isBadRequest());
  }

  @Test
  void sortByAmount() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("name", "Small", "amount", "10"));
    create(token, w, Map.of("name", "Big", "amount", "1000"));
    create(token, w, Map.of("name", "Mid", "amount", "100"));
    create(token, w, Map.of("name", "Mid2", "amount", "100.00"));

    // Largest first unless told; ties by id in the same direction.
    list(token, w, "sort", "AMOUNT").andExpect(names("Big", "Mid2", "Mid", "Small"));
    list(token, w, "sort", "AMOUNT", "direction", "ASC")
        .andExpect(names("Small", "Mid", "Mid2", "Big"));
    list(token, w, "sort", "AMOUNT", "size", "2", "page", "1")
        .andExpect(names("Mid", "Small"))
        .andExpect(jsonPath("$.totalElements").value(4));
    // The default: most recently updated first.
    list(token, w).andExpect(names("Mid2", "Mid", "Big", "Small"));
    list(token, w, "sort", "CREATED_AT").andExpect(names("Mid2", "Mid", "Big", "Small"));
    list(token, w, "sort", "amount").andExpect(status().isBadRequest());
    list(token, w, "direction", "UP").andExpect(status().isBadRequest());
  }

  @Test
  void sortByNameAndCloseDate() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    create(token, w, Map.of("name", "beta", "amount", "1"));
    create(token, w, Map.of("name", "Gamma", "amount", "1", "expectedCloseDate", "2026-12-01"));
    create(token, w, Map.of("name", "Alpha", "amount", "1", "expectedCloseDate", "2026-11-01"));
    create(token, w, Map.of("name", "delta", "amount", "1"));

    list(token, w, "sort", "NAME").andExpect(names("Alpha", "beta", "delta", "Gamma"));
    list(token, w, "sort", "NAME", "direction", "DESC")
        .andExpect(names("Gamma", "delta", "beta", "Alpha"));
    // Soonest first unless told; no date last in both directions, by id in the same direction.
    list(token, w, "sort", "EXPECTED_CLOSE_DATE")
        .andExpect(names("Alpha", "Gamma", "beta", "delta"));
    list(token, w, "sort", "EXPECTED_CLOSE_DATE", "direction", "DESC")
        .andExpect(names("Gamma", "Alpha", "delta", "beta"));
    list(token, w, "sort", "EXPECTED_CLOSE_DATE", "size", "3", "page", "1")
        .andExpect(names("delta"))
        .andExpect(jsonPath("$.totalElements").value(4));
    list(token, w, "sort", "EXPECTED_CLOSE_DATE", "closeTo", "2026-11-30")
        .andExpect(names("Alpha"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void archiveHidesAndRestoreReturns() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long a = create(token, w, Map.of("name", "A", "amount", "1"));
    create(token, w, Map.of("name", "B", "amount", "1"));

    String archivedAt =
        body(send(token, post(DEAL + "/archive", w, a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").exists())
                .andExpect(jsonPath("$.version").value(1)))
            .get("archivedAt")
            .asText();
    // As stored: a later read answers the same time.
    send(token, get(DEAL, w, a)).andExpect(jsonPath("$.archivedAt").value(archivedAt));
    send(token, post(DEAL + "/archive", w, a)).andExpect(jsonPath("$.version").value(1));
    list(token, w).andExpect(names("B"));
    list(token, w, "archived", "true").andExpect(names("A"));
    send(token, get(DEAL + "/history", w, a)).andExpect(jsonPath("$.length()").value(1));

    send(token, post(DEAL + "/restore", w, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.version").value(2));
    send(token, post(DEAL + "/restore", w, a)).andExpect(jsonPath("$.version").value(2));
    list(token, w).andExpect(names("A", "B"));
    assertThat(trail(w, a))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.RESTORE, AuditAction.ARCHIVE, AuditAction.CREATE);
  }

  @Test
  void otherWorkspaceIdIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long ofB = create(b, wb, Map.of("name", "B's", "amount", "1"));
    long stageOfA = stages(a, wa).get("Proposal");

    send(a, get(DEAL, wa, ofB)).andExpect(status().isNotFound());
    send(a, get(DEAL + "/history", wa, ofB)).andExpect(status().isNotFound());
    send(a, put(DEAL, wa, ofB), edit("Hijacked", "1", null, 0)).andExpect(status().isNotFound());
    move(a, wa, ofB, stageOfA, null, 0).andExpect(status().isNotFound());
    send(a, post(DEAL + "/archive", wa, ofB)).andExpect(status().isNotFound());
    send(a, post(DEAL + "/restore", wa, ofB)).andExpect(status().isNotFound());
    list(a, wa).andExpect(jsonPath("$.totalElements").value(0));

    send(b, get(DEAL, wb, ofB))
        .andExpect(jsonPath("$.name").value("B's"))
        .andExpect(jsonPath("$.stage.name").value("Qualification"))
        .andExpect(jsonPath("$.version").value(0));
    send(b, get(DEAL + "/history", wb, ofB)).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void stageDealCountAndDeleteInUse() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long demo = id(send(token, post(STAGES, w), Map.of("name", "Demo", "probability", 40)));
    Map<String, Long> stage = stages(token, w);
    long first = create(token, w, Map.of("name", "First", "amount", "1", "stageId", demo));
    long second = create(token, w, Map.of("name", "Second", "amount", "1", "stageId", demo));
    create(token, w, Map.of("name", "Third", "amount", "1"));
    send(token, post(DEAL + "/archive", w, second)).andExpect(status().isOk());

    // Active deals only.
    send(token, get(STAGES, w))
        .andExpect(
            jsonPath(
                "$[*].name",
                contains(
                    "Qualification",
                    "Needs analysis",
                    "Proposal",
                    "Negotiation",
                    "Demo",
                    "Won",
                    "Lost")))
        .andExpect(jsonPath("$[*].dealCount", contains(1, 0, 0, 0, 1, 0, 0)));

    send(token, delete(STAGES + "/{id}", w, demo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Stage has deals; move them first"));
    // An archived deal still holds its stage.
    move(token, w, first, stage.get("Qualification"), null, 0).andExpect(status().isOk());
    send(token, get(STAGES, w)).andExpect(jsonPath("$[4].dealCount").value(0));
    send(token, delete(STAGES + "/{id}", w, demo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Stage has deals; move them first"));
    // Once no deal is in it, it can go; the history keeps its name, without the id.
    move(token, w, second, stage.get("Qualification"), null, 1).andExpect(status().isOk());
    Map<Long, String> entered = new HashMap<>();
    for (long deal : List.of(first, second)) entered.put(deal, newestChange(token, w, deal));
    send(token, delete(STAGES + "/{id}", w, demo)).andExpect(status().isNoContent());
    send(token, get(STAGES, w))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].dealCount").value(2));
    for (long deal : List.of(first, second)) {
      // The newest entry lost its from-stage id: the deal still entered its stage when it did.
      send(token, get(DEAL, w, deal))
          .andExpect(jsonPath("$.stageEnteredAt").value(entered.get(deal)));
      assertThat(newestChange(token, w, deal)).isEqualTo(entered.get(deal));
      send(token, get(DEAL + "/history", w, deal))
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].fromStage.name").value("Demo"))
          .andExpect(jsonPath("$[0].fromStage.id").doesNotExist())
          .andExpect(jsonPath("$[0].toStage.id").value(stage.get("Qualification")))
          .andExpect(jsonPath("$[1].fromStage").doesNotExist())
          .andExpect(jsonPath("$[1].toStage.name").value("Demo"))
          .andExpect(jsonPath("$[1].toStage.id").doesNotExist());
    }

    // A renamed stage: old entries keep the name it had, new ones take the new name.
    JsonNode qualification = body(send(token, get(STAGES, w))).get(0);
    send(
            token,
            put(STAGES + "/{id}", w, qualification.get("id").asLong()),
            Map.of("name", "Discovery", "probability", 10, "version", qualification.get("version")))
        .andExpect(status().isOk());
    send(token, get(DEAL + "/history", w, first))
        .andExpect(jsonPath("$[0].toStage.name").value("Qualification"))
        .andExpect(jsonPath("$[0].toStage.id").value(stage.get("Qualification")));
    long fourth = create(token, w, Map.of("name", "Fourth", "amount", "1"));
    send(token, get(DEAL + "/history", w, fourth))
        .andExpect(jsonPath("$[0].toStage.name").value("Discovery"));
  }

  @Test
  void changedByIsNullOnceTheMemberLeaves() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    long id = create(rep, w, Map.of("name", "Rep's", "amount", "1"));
    send(owner, get(DEAL + "/history", w, id))
        .andExpect(jsonPath("$[0].changedBy.email").value(repEmail));

    send(rep, delete("/api/v1/workspaces/{workspaceId}/members/{userId}", w, userId(repEmail)))
        .andExpect(status().isNoContent());

    // The entry stays; who made it is no longer shown, and the deal is unassigned.
    send(owner, get(DEAL + "/history", w, id))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].toStage.name").value("Qualification"))
        .andExpect(jsonPath("$[0].changedBy").doesNotExist())
        .andExpect(jsonPath("$[0].changedAt").exists());
    send(owner, get(DEAL, w, id)).andExpect(jsonPath("$.owner").doesNotExist());
  }

  @Test
  void sourceInUseCannotBeDeleted() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long byLead = id(send(token, post(SOURCES, w), Map.of("name", "By lead")));
    long byContact = id(send(token, post(SOURCES, w), Map.of("name", "By contact")));
    long byDeal = id(send(token, post(SOURCES, w), Map.of("name", "By deal")));
    long unused = id(send(token, post(SOURCES, w), Map.of("name", "Unused")));
    id(send(token, post(LEADS, w), Map.of("firstName", "Ada", "sourceId", byLead)));
    id(send(token, post(CONTACTS, w), Map.of("firstName", "Bob", "sourceId", byContact)));
    long deal = create(token, w, Map.of("name", "Deal", "amount", "1", "sourceId", byDeal));
    // Archived records count: their foreign keys still point at the source.
    send(token, post(DEAL + "/archive", w, deal)).andExpect(status().isOk());

    for (long used : List.of(byLead, byContact, byDeal)) {
      send(token, delete(SOURCES + "/{id}", w, used))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("Lead source is in use; archive it instead"));
    }
    send(token, delete(SOURCES + "/{id}", w, unused)).andExpect(status().isNoContent());
    send(token, get(SOURCES, w))
        .andExpect(jsonPath("$[*].name", hasItems("By lead", "By contact", "By deal")));
  }

  @Test
  void companyOpenDealCount() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Map<String, Long> stage = stages(token, w);
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme")));
    long other = id(send(token, post(COMPANIES, w), Map.of("name", "Other")));
    create(token, w, Map.of("name", "Open 1", "amount", "1", "companyId", acme));
    create(
        token,
        w,
        Map.of(
            "name", "Open 2", "amount", "1", "companyId", acme, "stageId", stage.get("Proposal")));
    long won = create(token, w, Map.of("name", "Won", "amount", "1", "companyId", acme));
    long lost = create(token, w, Map.of("name", "Lost", "amount", "1", "companyId", acme));
    long archived = create(token, w, Map.of("name", "Archived", "amount", "1", "companyId", acme));
    move(token, w, won, stage.get("Won"), null, 0).andExpect(status().isOk());
    move(token, w, lost, stage.get("Lost"), "No", 0).andExpect(status().isOk());
    send(token, post(DEAL + "/archive", w, archived)).andExpect(status().isOk());

    send(token, get(COMPANIES + "/{id}", w, acme)).andExpect(jsonPath("$.openDealCount").value(2));
    send(token, get(COMPANIES + "/{id}", w, other)).andExpect(jsonPath("$.openDealCount").value(0));
    send(token, get(COMPANIES, w))
        .andExpect(jsonPath("$.content[*].name", contains("Acme", "Other")))
        .andExpect(jsonPath("$.content[*].openDealCount", contains(2, 0)));
    // Reopening a won deal counts it again.
    move(token, w, won, stage.get("Negotiation"), null, 1).andExpect(status().isOk());
    send(token, get(COMPANIES + "/{id}", w, acme)).andExpect(jsonPath("$.openDealCount").value(3));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().getId();
  }

  private long create(String token, long w, Map<String, ?> input) throws Exception {
    return id(send(token, post(DEALS, w), input));
  }

  private long id(ResultActions created) throws Exception {
    return body(created.andExpect(status().isCreated())).get("id").asLong();
  }

  /** The workspace's stage ids by name. */
  private Map<String, Long> stages(String token, long w) throws Exception {
    Map<String, Long> ids = new HashMap<>();
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      ids.put(s.get("name").asText(), s.get("id").asLong());
    }
    return ids;
  }

  /** One of the lead sources every workspace starts with. */
  private JsonNode firstSource(String token, long w) throws Exception {
    return body(send(token, get(SOURCES, w))).get(0);
  }

  /** A PUT body: name, amount, owner (null unassigns), version and any other fields as pairs. */
  private static Map<String, Object> edit(
      String name, Object amount, Long ownerId, long version, Object... more) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("amount", amount);
    body.put("ownerId", ownerId);
    body.put("version", version);
    for (int i = 0; i < more.length; i += 2) body.put((String) more[i], more[i + 1]);
    return body;
  }

  private ResultActions move(
      String token, long w, long id, long stageId, String lostReason, long version)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("stageId", stageId);
    body.put("lostReason", lostReason);
    body.put("version", version);
    return send(token, post(DEAL + "/move", w, id), body);
  }

  private void malformed(String token, long w, Map<String, ?> input) throws Exception {
    send(token, post(DEALS, w), input).andExpect(status().isBadRequest());
  }

  private void invalid(String token, long w, Map<String, ?> input, String field) throws Exception {
    send(token, post(DEALS, w), input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + field).exists());
  }

  private ResultActions list(String token, long w, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(DEALS, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }

  private static ResultMatcher names(String... names) {
    return jsonPath("$.content[*].name", contains(names));
  }

  private static ResultMatcher namesAnyOrder(String... names) {
    return jsonPath("$.content[*].name", containsInAnyOrder(names));
  }

  /** The {@code changedAt} of the deal's newest history entry. */
  private String newestChange(String token, long w, long id) throws Exception {
    return body(send(token, get(DEAL + "/history", w, id))).get(0).get("changedAt").asText();
  }

  /** The deal's audit events, newest first. */
  private List<AuditEvent> trail(long w, long id) {
    return events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
        .filter(e -> e.getEntityType() == AuditEntityType.DEAL && e.getEntityId() == id)
        .toList();
  }
}
