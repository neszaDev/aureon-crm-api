package com.example.boilerplate.crm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class PipelineStageIT extends WorkspaceTestSupport {
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String STAGE = STAGES + "/{id}";
  static final String ORDER = STAGES + "/order";

  @Autowired AuditEventRepository events;

  @Test
  void newWorkspaceHasDefaultStages() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, get(STAGES, w))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].name",
                contains(
                    "Qualification", "Needs analysis", "Proposal", "Negotiation", "Won", "Lost")))
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5, 6)))
        .andExpect(jsonPath("$[*].probability", contains(10, 25, 50, 75, 100, 0)))
        .andExpect(jsonPath("$[*].kind", contains("OPEN", "OPEN", "OPEN", "OPEN", "WON", "LOST")))
        .andExpect(jsonPath("$[*].version", everyItem(is(0))));
  }

  @Test
  void addRenameReorderDelete() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    long demo =
        body(send(token, post(STAGES, w), Map.of("name", "Demo", "probability", 40))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Demo"))
                .andExpect(jsonPath("$.position").value(5))
                .andExpect(jsonPath("$.probability").value(40))
                .andExpect(jsonPath("$.kind").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0)))
            .get("id")
            .asLong();
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
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5, 6, 7)));

    send(
            token,
            put(STAGE, w, demo),
            Map.of("name", "Product demo", "probability", 45, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Product demo"))
        .andExpect(jsonPath("$.probability").value(45))
        .andExpect(jsonPath("$.position").value(5))
        .andExpect(jsonPath("$.version").value(1));

    List<Long> open = openIds(token, w);
    List<Long> order = new ArrayList<>(List.of(demo));
    order.addAll(open.subList(0, 4));
    // PUT /order must reach the reorder handler, not PUT /{id}.
    send(token, put(ORDER, w), Map.of("stageIds", order))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].name",
                contains(
                    "Product demo",
                    "Qualification",
                    "Needs analysis",
                    "Proposal",
                    "Negotiation",
                    "Won",
                    "Lost")))
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5, 6, 7)));

    send(token, delete(STAGE, w, demo)).andExpect(status().isNoContent());
    send(token, get(STAGES, w))
        .andExpect(
            jsonPath(
                "$[*].name",
                contains(
                    "Qualification", "Needs analysis", "Proposal", "Negotiation", "Won", "Lost")))
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5, 6)));

    List<AuditEvent> trail =
        events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(w).stream()
            .filter(e -> e.getEntityType() == AuditEntityType.PIPELINE_STAGE)
            .filter(e -> e.getEntityId() == demo)
            .toList();
    assertThat(trail)
        .extracting(AuditEvent::getAction)
        .containsExactly(
            AuditAction.DELETE, AuditAction.UPDATE, AuditAction.UPDATE, AuditAction.CREATE);
    assertThat(trail.get(1).getChanges()).isEqualTo(Map.of("position", Map.of("from", 5, "to", 1)));
    assertThat(trail.get(2).getChanges())
        .isEqualTo(
            Map.of(
                "name", Map.of("from", "Demo", "to", "Product demo"),
                "probability", Map.of("from", 40, "to", 45)));
    assertThat(trail).extracting(AuditEvent::getEntityLabel).first().isEqualTo("Product demo");
    // The DELETE keeps the stage's last state, every stored field as a "from" (dealCount is
    // counted, not stored).
    Map<String, Object> deleted = trail.get(0).getChanges();
    assertThat(deleted).containsOnlyKeys("id", "name", "position", "probability", "kind");
    assertThat(deleted.get("id")).isEqualTo(gone((int) demo));
    assertThat(deleted.get("name")).isEqualTo(gone("Product demo"));
    assertThat(deleted.get("position")).isEqualTo(gone(1));
    assertThat(deleted.get("probability")).isEqualTo(gone(45));
    assertThat(deleted.get("kind")).isEqualTo(gone("OPEN"));
  }

  @Test
  void reorderMustListExactlyTheOpenStages() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long other = createWorkspace(token, "Other");
    List<Long> open = openIds(token, w);
    long won = idOf(token, w, "Won");
    long foreign = openIds(token, other).get(0);

    List<List<Long>> wrong =
        List.of(
            open.subList(0, 3),
            with(open, won),
            List.of(open.get(0), open.get(0), open.get(1), open.get(2)),
            with(open.subList(0, 3), foreign),
            List.of());
    for (List<Long> ids : wrong) {
      send(token, put(ORDER, w), Map.of("stageIds", ids))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors.stageIds").exists());
    }

    send(token, get(STAGES, w))
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5, 6)))
        .andExpect(jsonPath("$[*].version", everyItem(is(0))));
  }

  @Test
  void cannotDeleteWonLostOrLastOpen() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    List<Long> open = openIds(token, w);

    for (String closed : List.of("Won", "Lost")) {
      send(token, delete(STAGE, w, idOf(token, w, closed))).andExpect(status().isConflict());
    }
    for (long id : open.subList(0, 3)) {
      send(token, delete(STAGE, w, id)).andExpect(status().isNoContent());
    }
    send(token, delete(STAGE, w, open.get(3))).andExpect(status().isConflict());

    send(token, get(STAGES, w))
        .andExpect(jsonPath("$[*].name", contains("Negotiation", "Won", "Lost")))
        .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));
  }

  /**
   * Two deletes at once of the last two open stages: deletes and reorders take the pipeline's lock,
   * so the second sees one open stage left and refuses. Several rounds, so a lucky ordering can't
   * hide a race.
   */
  @Test
  void concurrentDeletesKeepAnOpenStage() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    List<Long> open = openIds(token, w);
    for (long id : open.subList(2, open.size())) {
      send(token, delete(STAGE, w, id)).andExpect(status().isNoContent());
    }
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        List<Long> pair = openIds(token, w);
        assertThat(pair).hasSize(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResultActions>> futures = new ArrayList<>();
        for (long id : pair) {
          Callable<ResultActions> deleteIt =
              () -> {
                start.await();
                return send(token, delete(STAGE, w, id));
              };
          futures.add(pool.submit(deleteIt));
        }
        start.countDown();
        List<String> answers = new ArrayList<>();
        for (Future<ResultActions> f : futures) {
          MvcResult r = f.get(30, TimeUnit.SECONDS).andReturn();
          int status = r.getResponse().getStatus();
          answers.add(
              status == 204
                  ? "204"
                  : status + " " + json.readTree(r.getResponse().getContentAsString()).get("code"));
        }

        assertThat(answers).containsExactlyInAnyOrder("204", "409 \"STAGE_PROTECTED\"");
        assertThat(openIds(token, w)).hasSize(1);
        send(token, post(STAGES, w), Map.of("name", "Round " + round, "probability", 10))
            .andExpect(status().isCreated());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void duplicateNameIs409() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, post(STAGES, w), Map.of("name", "proposal", "probability", 50))
        .andExpect(status().isConflict());
    send(
            token,
            put(STAGE, w, idOf(token, w, "Qualification")),
            Map.of("name", "PROPOSAL", "probability", 10, "version", 0))
        .andExpect(status().isConflict());
    // Changing the case of its own name is not a duplicate.
    send(
            token,
            put(STAGE, w, idOf(token, w, "Proposal")),
            Map.of("name", "proposal", "probability", 50, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("proposal"));
  }

  @Test
  void invalidInputIs400() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    send(token, post(STAGES, w), Map.of("name", " ", "probability", 101))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists())
        .andExpect(jsonPath("$.fieldErrors.probability").exists());
    send(token, post(STAGES, w), Map.of("name", "x".repeat(61), "probability", -1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists())
        .andExpect(jsonPath("$.fieldErrors.probability").exists());
    send(token, put(STAGE, w, 999_999_999L), Map.of("name", "X", "probability", 1, "version", 0))
        .andExpect(status().isNotFound());
  }

  @Test
  void omittedProbabilityIs400NotZero() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long stage = body(send(token, get(STAGES, w))).get(0).get("id").asLong();

    send(token, post(STAGES, w), Map.of("name", "Contract"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.probability").exists());
    send(token, put(STAGE, w, stage), Map.of("name", "Renamed", "version", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.probability").exists());
  }

  @Test
  void salesRepCanReadButNotWrite() throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    List<Long> open = openIds(owner, w);

    send(rep, get(STAGES, w)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
    send(rep, post(STAGES, w), Map.of("name", "Demo", "probability", 40))
        .andExpect(status().isForbidden());
    send(rep, put(ORDER, w), Map.of("stageIds", open)).andExpect(status().isForbidden());
    send(rep, delete(STAGE, w, open.get(0))).andExpect(status().isForbidden());
  }

  @Test
  void staleVersionIs409() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long id = idOf(token, w, "Qualification");

    send(token, put(STAGE, w, id), Map.of("name", "Qualified", "probability", 10, "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    send(token, put(STAGE, w, id), Map.of("name", "Again", "probability", 10, "version", 0))
        .andExpect(status().isConflict());
    send(token, put(STAGE, w, id), Map.of("name", "Again", "probability", 10))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.version").exists());

    assertThat(idOf(token, w, "Qualified")).isEqualTo(id);
  }

  private List<Long> openIds(String token, long w) throws Exception {
    List<Long> ids = new ArrayList<>();
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      if (s.get("kind").asText().equals("OPEN")) ids.add(s.get("id").asLong());
    }
    return ids;
  }

  private long idOf(String token, long w, String name) throws Exception {
    for (JsonNode s : body(send(token, get(STAGES, w)))) {
      if (s.get("name").asText().equals(name)) return s.get("id").asLong();
    }
    throw new AssertionError("No stage " + name);
  }

  private static List<Long> with(List<Long> ids, long extra) {
    List<Long> all = new ArrayList<>(ids);
    all.add(extra);
    return all;
  }

  /** A field of a deleted record's last state: {@code {from: value, to: null}}. */
  private static Map<String, Object> gone(Object from) {
    Map<String, Object> change = new HashMap<>();
    change.put("from", from);
    change.put("to", null);
    return change;
  }
}
