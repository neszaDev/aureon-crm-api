package com.example.boilerplate.crm.deal;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class DealBoardIT extends WorkspaceTestSupport {
  static final String DEALS = "/api/v1/workspaces/{workspaceId}/deals";
  static final String BOARD = DEALS + "/board";
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String COMPANIES = "/api/v1/workspaces/{workspaceId}/companies";

  @Test
  void boardGroupsByStageWithTotalsPerCurrency() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Acme");
    String repEmail = uniqueEmail();
    String rep = tokenFor(repEmail);
    addMember(w, repEmail, WorkspaceRole.SALES_REP);
    Map<String, Long> stage = stages(token, w);
    long proposal = stage.get("Proposal");
    long acme = id(send(token, post(COMPANIES, w), Map.of("name", "Acme")));
    long usdOne =
        create(
            token,
            w,
            Map.of(
                "name", "USD one",
                "amount", "1250.10",
                "currency", "USD",
                "stageId", proposal,
                "companyId", acme));
    create(
        token,
        w,
        Map.of("name", "USD two", "amount", "99.95", "currency", "usd", "stageId", proposal));
    create(rep, w, Map.of("name", "EUR one", "amount", "10", "stageId", proposal));
    create(token, w, Map.of("name", "Qual", "amount", "5"));
    // Another workspace's deals never show.
    String other = tokenFor(uniqueEmail());
    long wo = createWorkspace(other, "Other");
    create(
        other,
        wo,
        Map.of("name", "Theirs", "amount", "7", "stageId", stages(other, wo).get("Proposal")));

    board(token, w)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.stages[*].stage.name",
                contains(
                    "Qualification", "Needs analysis", "Proposal", "Negotiation", "Won", "Lost")))
        .andExpect(jsonPath("$.stages[*].count", contains(1, 0, 3, 0, 0, 0)))
        .andExpect(jsonPath("$.stages[2].stage.id").value(proposal))
        .andExpect(jsonPath("$.stages[2].stage.kind").value("OPEN"))
        .andExpect(jsonPath("$.stages[2].stage.dealCount").value(3))
        // Per currency, by code, exact sums as strings.
        .andExpect(jsonPath("$.stages[2].totals[*].currency", contains("EUR", "USD")))
        .andExpect(jsonPath("$.stages[2].totals[*].amount", contains("10.00", "1350.05")))
        // Most recently updated first.
        .andExpect(jsonPath("$.stages[2].deals[*].name", contains("EUR one", "USD two", "USD one")))
        .andExpect(jsonPath("$.stages[2].deals[2].amount").value("1250.10"))
        .andExpect(jsonPath("$.stages[2].deals[2].stage.name").value("Proposal"))
        .andExpect(jsonPath("$.stages[2].deals[2].status").value("OPEN"))
        .andExpect(jsonPath("$.stages[2].deals[2].company.name").value("Acme"))
        .andExpect(jsonPath("$.stages[2].deals[2].owner.email").value(email))
        .andExpect(jsonPath("$.stages[0].totals[*].amount", contains("5.00")))
        // Stages without deals are there, empty.
        .andExpect(jsonPath("$.stages[1].totals", empty()))
        .andExpect(jsonPath("$.stages[1].deals", empty()))
        .andExpect(jsonPath("$.stages[4].count").value(0));

    // An edit brings a deal to the top of its column.
    Map<String, Object> edit = new HashMap<>();
    edit.put("name", "USD one");
    edit.put("amount", "1250.10");
    edit.put("currency", "USD");
    edit.put("companyId", acme);
    edit.put("ownerId", userId(email));
    edit.put("notes", "Touched");
    edit.put("version", 0);
    send(token, put(DEALS + "/{id}", w, usdOne), edit).andExpect(status().isOk());
    board(token, w)
        .andExpect(
            jsonPath("$.stages[2].deals[*].name", contains("USD one", "EUR one", "USD two")));

    // The filters narrow the deals, the counts and the totals alike; the stage's own count stays.
    board(token, w, "companyId", String.valueOf(acme))
        .andExpect(jsonPath("$.stages[*].count", contains(0, 0, 1, 0, 0, 0)))
        .andExpect(jsonPath("$.stages[2].totals[*].amount", contains("1250.10")))
        .andExpect(jsonPath("$.stages[2].stage.dealCount").value(3));
    board(token, w, "q", "usd")
        .andExpect(jsonPath("$.stages[2].count").value(2))
        .andExpect(jsonPath("$.stages[2].totals[*].currency", contains("USD")))
        .andExpect(jsonPath("$.stages[2].totals[*].amount", contains("1350.05")));
    board(token, w, "q", "acme")
        .andExpect(jsonPath("$.stages[2].deals[*].name", contains("USD one")));
    board(token, w, "ownerId", String.valueOf(userId(repEmail)))
        .andExpect(jsonPath("$.stages[*].count", contains(0, 0, 1, 0, 0, 0)))
        .andExpect(jsonPath("$.stages[2].deals[*].name", contains("EUR one")));
    board(token, w, "q", "%").andExpect(jsonPath("$.stages[*].count", contains(0, 0, 0, 0, 0, 0)));
    board(token, w, "q", "x".repeat(201)).andExpect(status().isBadRequest());
    board(token, w, "ownerId", "me").andExpect(status().isBadRequest());

    // A move takes the deal, its count and its total to the new column.
    Map<String, Object> move = new HashMap<>();
    move.put("stageId", stage.get("Won"));
    move.put("version", 1);
    send(token, post(DEALS + "/{id}/move", w, usdOne), move).andExpect(status().isOk());
    board(token, w)
        .andExpect(jsonPath("$.stages[*].count", contains(1, 0, 2, 0, 1, 0)))
        .andExpect(jsonPath("$.stages[4].totals[*].amount", contains("1250.10")))
        .andExpect(jsonPath("$.stages[4].deals[0].status").value("WON"))
        .andExpect(jsonPath("$.stages[2].totals[*].amount", contains("10.00", "99.95")));
  }

  @Test
  void boardCapsAt50ButCountsAll() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    for (int i = 1; i <= 51; i++) create(token, w, Map.of("name", "Deal " + i, "amount", "1.01"));

    board(token, w)
        .andExpect(jsonPath("$.stages[0].count").value(51))
        .andExpect(jsonPath("$.stages[0].stage.dealCount").value(51))
        .andExpect(jsonPath("$.stages[0].deals.length()").value(50))
        .andExpect(jsonPath("$.stages[0].deals[0].name").value("Deal 51"))
        .andExpect(jsonPath("$.stages[0].deals[49].name").value("Deal 2"))
        .andExpect(jsonPath("$.stages[0].totals[*].amount", contains("51.51")));
  }

  @Test
  void boardIgnoresArchived() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    long negotiation = stages(token, w).get("Negotiation");
    long kept = create(token, w, Map.of("name", "Kept", "amount", "3", "stageId", negotiation));
    long gone = create(token, w, Map.of("name", "Gone", "amount", "4", "stageId", negotiation));
    send(token, post(DEALS + "/{id}/archive", w, gone)).andExpect(status().isOk());

    board(token, w)
        .andExpect(jsonPath("$.stages[3].count").value(1))
        .andExpect(jsonPath("$.stages[3].stage.dealCount").value(1))
        .andExpect(jsonPath("$.stages[3].deals[*].id", contains((int) kept)))
        .andExpect(jsonPath("$.stages[3].totals[*].amount", contains("3.00")));
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

  private ResultActions board(String token, long w, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(BOARD, w);
    for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
    return send(token, request);
  }
}
