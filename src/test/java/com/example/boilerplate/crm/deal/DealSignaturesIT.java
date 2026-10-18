package com.example.boilerplate.crm.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code signatures} on every {@code DealSummary}: who moved the deal into each stage, the newest
 * move into a stage deciding (the web's {@code stageSignatures}).
 */
class DealSignaturesIT extends WorkspaceTestSupport {
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";
  static final String DEAL = DEALS + "/{id}";
  static final String BOARD = DEALS + "/board";
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String MEMBER = "/api/v1/workspaces/{workspaceId}/members/{userId}";

  @Autowired DealStageChangeRepository history;
  @Autowired EntityManagerFactory entityManagerFactory;

  /** Created in A by X, A to B by Y, B to C by Z, C to B by W: A is X's, B is W's, C is Z's. */
  @Test
  void theNewestMoveIntoEachStageSigns() throws Exception {
    String x = uniqueEmail();
    String owner = tokenFor(x);
    long w = createWorkspace(owner, "Acme");
    String y = member(w, WorkspaceRole.MANAGER);
    String z = member(w, WorkspaceRole.MANAGER);
    String wEmail = member(w, WorkspaceRole.MANAGER);
    Map<String, Long> stage = stages(owner, w);
    long a = stage.get("Qualification");
    long b = stage.get("Proposal");
    long c = stage.get("Negotiation");
    long deal = create(owner, w, Map.of("name", "Moved", "amount", "10"));
    create(tokenOf(y), w, Map.of("name", "Untouched", "amount", "1"));
    move(tokenOf(y), w, deal, b, 0);
    move(tokenOf(z), w, deal, c, 1);
    move(tokenOf(wEmail), w, deal, b, 2);

    Map<Long, String> expected = Map.of(a, x, b, wEmail, c, z);
    assertThat(signatures(board(owner, w), "Moved")).isEqualTo(expected);
    assertThat(signatures(list(owner, w), "Moved")).isEqualTo(expected);
    // Per deal: the other deal's one entry is its creator's.
    assertThat(signatures(board(owner, w), "Untouched")).isEqualTo(Map.of(a, y));
    assertThat(signatures(list(owner, w), "Untouched")).isEqualTo(Map.of(a, y));
    // The seal is the member, whole, as the history shows them.
    JsonNode by = board(owner, w).get("Moved").get("signatures").get(0).get("by");
    assertThat(by.get("userId").asLong()).isEqualTo(userId(x));
    assertThat(by.get("email").asText()).isEqualTo(x);
  }

  /** The member is gone: no {@code by}, as in the history; the stage is still listed. */
  @Test
  void aDepartedMembersMoveHasNoByButItsStageIsListed() throws Exception {
    String x = uniqueEmail();
    String owner = tokenFor(x);
    long w = createWorkspace(owner, "Acme");
    String repEmail = member(w, WorkspaceRole.MANAGER);
    Map<String, Long> stage = stages(owner, w);
    long deal = create(owner, w, Map.of("name", "Left behind", "amount", "10"));
    move(tokenOf(repEmail), w, deal, stage.get("Proposal"), 0);

    send(tokenOf(repEmail), delete(MEMBER, w, userId(repEmail))).andExpect(status().isNoContent());

    Map<Long, String> expected = new HashMap<>();
    expected.put(stage.get("Qualification"), x);
    expected.put(stage.get("Proposal"), null);
    assertThat(signatures(board(owner, w), "Left behind")).isEqualTo(expected);
    assertThat(signatures(list(owner, w), "Left behind")).isEqualTo(expected);
    send(owner, get(DEAL + "/history", w, deal))
        .andExpect(jsonPath("$[0].toStage.name").value("Proposal"))
        .andExpect(jsonPath("$[0].changedBy").doesNotExist());
  }

  /** A deleted stage's entries have no stage id: nothing to sign. */
  @Test
  void aDeletedStageIsNotListed() throws Exception {
    String x = uniqueEmail();
    String owner = tokenFor(x);
    long w = createWorkspace(owner, "Acme");
    long demo = id(send(owner, post(STAGES, w), Map.of("name", "Demo", "probability", 40)));
    Map<String, Long> stage = stages(owner, w);
    long deal = create(owner, w, Map.of("name", "Passed", "amount", "10", "stageId", demo));
    move(owner, w, deal, stage.get("Proposal"), 0);
    assertThat(signatures(board(owner, w), "Passed"))
        .isEqualTo(Map.of(demo, x, stage.get("Proposal"), x));

    send(owner, delete(STAGES + "/{id}", w, demo)).andExpect(status().isNoContent());

    assertThat(signatures(board(owner, w), "Passed")).isEqualTo(Map.of(stage.get("Proposal"), x));
    assertThat(signatures(list(owner, w), "Passed")).isEqualTo(Map.of(stage.get("Proposal"), x));
  }

  /** Each board shows its own workspace's signers; the query binds the workspace itself. */
  @Test
  void signaturesComeOnlyFromTheOwnWorkspace() throws Exception {
    String xa = uniqueEmail();
    String a = tokenFor(xa);
    long wa = createWorkspace(a, "A");
    String xb = uniqueEmail();
    String b = tokenFor(xb);
    long wb = createWorkspace(b, "B");
    long ofA = create(a, wa, Map.of("name", "Of A", "amount", "1"));
    long ofB = create(b, wb, Map.of("name", "Of B", "amount", "1"));
    long stageA = stages(a, wa).get("Proposal");
    long stageB = stages(b, wb).get("Proposal");
    move(a, wa, ofA, stageA, 0);
    move(b, wb, ofB, stageB, 0);

    assertThat(signatures(board(a, wa), "Of A"))
        .containsOnlyKeys(stages(a, wa).get("Qualification"), stageA)
        .containsValue(xa)
        .doesNotContainValue(xb);
    assertThat(signatures(board(b, wb), "Of B"))
        .containsOnlyKeys(stages(b, wb).get("Qualification"), stageB)
        .containsValue(xb)
        .doesNotContainValue(xa);
    assertThat(board(a, wa)).doesNotContainKey("Of B");
    // An outsider gets the other board's 404, not its signatures.
    send(a, get(BOARD, wb)).andExpect(status().isNotFound());
    send(a, get(DEALS, wb)).andExpect(status().isNotFound());

    // The native query is not tenant-filtered: only its own bind keeps B's rows out of A's page.
    Membership inB = new Membership(wb, userId(xb), xb, WorkspaceRole.OWNER);
    List<Object[]> rows =
        WorkspaceContext.runAs(inB, () -> history.signatures(wb, List.of(ofA, ofB)));
    assertThat(rows).extracting(row -> row[0]).containsOnly(ofB);
  }

  /**
   * A board of many deals asks for the signatures once, and costs the statements of a board of two
   * (Hibernate statistics: no query per deal or per signature).
   */
  @Test
  void aBoardOfManyDealsReadsTheSignaturesOnce() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    Map<String, Long> stage = stages(owner, w);
    for (int i = 0; i < 2; i++) moved(owner, w, "Few " + i, stage.get("Proposal"));
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      board(owner, w);
      long few = statistics.getPrepareStatementCount();
      assertThat(signatureQueries(statistics)).isEqualTo(1);

      for (int i = 0; i < 4; i++) moved(owner, w, "Many " + i, stage.get("Proposal"));
      statistics.clear();
      board(owner, w);

      assertThat(signatureQueries(statistics)).isEqualTo(1);
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(few);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  /** How many times the native signatures query ran since the statistics were cleared. */
  private static long signatureQueries(Statistics statistics) {
    return Arrays.stream(statistics.getQueries())
        .filter(sql -> sql.toLowerCase().contains("distinct on"))
        .mapToLong(sql -> statistics.getQueryStatistics(sql).getExecutionCount())
        .sum();
  }

  /** A deal created and moved to {@code stageId}. */
  private void moved(String token, long w, String name, long stageId) throws Exception {
    move(token, w, create(token, w, Map.of("name", name, "amount", "1")), stageId, 0);
  }

  private final Map<String, String> tokens = new HashMap<>();

  /** Registers a user, adds them to the workspace with {@code role} and returns their email. */
  private String member(long w, WorkspaceRole role) throws Exception {
    String email = uniqueEmail();
    tokens.put(email, tokenFor(email));
    addMember(w, email, role);
    return email;
  }

  private String tokenOf(String email) {
    return tokens.get(email);
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

  private Map<String, Long> stages(String token, long w) throws Exception {
    Map<String, Long> ids = new HashMap<>();
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      ids.put(s.get("name").asText(), s.get("id").asLong());
    }
    return ids;
  }

  private void move(String token, long w, long id, long stageId, long version) throws Exception {
    send(token, post(DEAL + "/move", w, id), Map.of("stageId", stageId, "version", version))
        .andExpect(status().isOk());
  }

  /** The board's cards by deal name. */
  private Map<String, JsonNode> board(String token, long w) throws Exception {
    Map<String, JsonNode> cards = new HashMap<>();
    for (JsonNode column : body(send(token, get(BOARD, w))).get("stages")) {
      for (JsonNode card : column.get("deals")) cards.put(card.get("name").asText(), card);
    }
    return cards;
  }

  /** The list's rows by deal name. */
  private Map<String, JsonNode> list(String token, long w) throws Exception {
    Map<String, JsonNode> rows = new HashMap<>();
    for (JsonNode row : body(send(token, get(DEALS, w))).get("content")) {
      rows.put(row.get("name").asText(), row);
    }
    return rows;
  }

  /** The deal's signatures as stage id to the signer's email (null: no {@code by}). */
  private static Map<Long, String> signatures(Map<String, JsonNode> byName, String name) {
    Map<Long, String> signed = new HashMap<>();
    for (JsonNode s : byName.get(name).get("signatures")) {
      signed.put(s.get("stageId").asLong(), s.has("by") ? s.get("by").get("email").asText() : null);
    }
    return signed;
  }
}
