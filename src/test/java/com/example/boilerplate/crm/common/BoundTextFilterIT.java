package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The text a client sends in a filter ({@code q}, {@code industry}, {@code tag}) reaches PostgreSQL
 * as a bound parameter, never written into the SQL text, and {@code '}, {@code \}, {@code %} and
 * {@code _} in it mean themselves. Every list that filters on text, its export and the deal board.
 */
class BoundTextFilterIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";
  static final String NEEDLE = "O'Brien\\%_x";

  /** A list, the field of its records the text goes to, and what a create needs besides it. */
  record Listing(String path, String field, String ref, Map<String, Object> more) {}

  static final List<Listing> LISTINGS =
      List.of(
          new Listing("/companies", "name", "C", Map.of()),
          new Listing("/contacts", "firstName", "P", Map.of()),
          new Listing("/leads", "firstName", "L", Map.of()),
          new Listing("/deals", "name", "D", Map.of("amount", "1")),
          new Listing("/tasks", "title", "T", Map.of()));

  /** What q finds: the text inside another, in any case. */
  static final List<String> FOUND = List.of("A O'Brien\\%_x B", "a o'BRIEN\\%_X b");

  /**
   * What q must not find: no backslash, wildcards that would match if {@code % _ \} were not
   * literal (both, only {@code _}, only {@code %}), no quote.
   */
  static final List<String> NOT_FOUND =
      List.of(
          "A O'Brien%_x B",
          "A O'Brien\\abZx B", "A O'Brien\\%Zx B", "A O'Brien\\ab_x B", "A OBrien\\%_x B");

  @Test
  void qMeansTheTextItself() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    for (Listing l : LISTINGS) {
      for (String value : FOUND) create(token, w, l, l.field(), value, Map.of());
      for (String value : NOT_FOUND) create(token, w, l, l.field(), value, Map.of());

      assertThat(names(token, w, l, "")).as(l.path()).containsExactlyInAnyOrderElementsOf(FOUND);
      if (!l.path().equals("/tasks")) {
        assertThat(exported(token, w, l, ""))
            .as(l.path() + "/export")
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(ids(token, w, l, ""));
      }
    }
    // The deal board filters like the list.
    JsonNode board =
        body(send(token, get(W + "/deals/board", w).param("q", NEEDLE)).andExpect(status().isOk()));
    List<String> deals = new ArrayList<>();
    board
        .get("stages")
        .forEach(s -> s.get("deals").forEach(d -> deals.add(d.get("name").asText())));
    assertThat(deals).containsExactlyInAnyOrderElementsOf(FOUND);
  }

  @Test
  void industryIsExactInAnyCase() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Listing companies = LISTINGS.get(0);
    List<String> found = List.of(NEEDLE, "o'brien\\%_X");
    for (String industry : found) {
      create(token, w, companies, "name", "Co " + industry, Map.of("industry", industry));
    }
    // Longer, wildcards that would match, no quote, no backslash.
    for (String industry :
        List.of(
            NEEDLE + "y",
            "O'Brien\\abZx",
            "O'Brien\\%Zx",
            "O'Brien\\ab_x",
            "OBrien\\%_x",
            "O'Brien%_x",
            "xO'Brien\\%_x")) {
      create(token, w, companies, "name", "Co " + industry, Map.of("industry", industry));
    }

    assertThat(names(token, w, companies, "industry"))
        .containsExactlyInAnyOrder("Co " + NEEDLE, "Co o'brien\\%_X");
    assertThat(exported(token, w, companies, "industry"))
        .containsExactlyInAnyOrderElementsOf(ids(token, w, companies, "industry"))
        .hasSize(2);
  }

  @Test
  void tagIsExactInAnyCase() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");
    Listing contacts = LISTINGS.get(1);
    create(token, w, contacts, "firstName", "Found", Map.of("tags", List.of("vip", NEEDLE)));
    create(token, w, contacts, "firstName", "Lower", Map.of("tags", List.of("o'brien\\%_X")));
    for (String tag :
        List.of(
            NEEDLE + "y",
            "O'Brien\\abZx",
            "O'Brien\\%Zx",
            "O'Brien\\ab_x",
            "OBrien\\%_x",
            "O'Brien%_x")) {
      create(token, w, contacts, "firstName", "No " + tag, Map.of("tags", List.of(tag)));
    }

    assertThat(names(token, w, contacts, "tag")).containsExactlyInAnyOrder("Found", "Lower");
    assertThat(exported(token, w, contacts, "tag"))
        .containsExactlyInAnyOrderElementsOf(ids(token, w, contacts, "tag"))
        .hasSize(2);
  }

  @Test
  void theTextIsNeverWrittenIntoTheSql() throws Exception {
    String token = tokenFor(uniqueEmail());
    long w = createWorkspace(token, "Acme");

    Logger sql = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    Level before = sql.getLevel();
    ListAppender<ILoggingEvent> statements = new ListAppender<>();
    statements.start();
    sql.addAppender(statements);
    sql.setLevel(Level.DEBUG);
    try {
      for (Listing l : LISTINGS) {
        names(token, w, l, "");
        if (!l.path().equals("/tasks")) exported(token, w, l, "");
      }
      send(token, get(W + "/deals/board", w).param("q", NEEDLE)).andExpect(status().isOk());
      names(token, w, LISTINGS.get(0), "industry");
      exported(token, w, LISTINGS.get(0), "industry");
      names(token, w, LISTINGS.get(1), "tag");
      exported(token, w, LISTINGS.get(1), "tag");
    } finally {
      sql.detachAppender(statements);
      sql.setLevel(before);
    }

    List<String> queries =
        statements.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    // The filters ran (every one of them has a like, an equality or array_position), and none of
    // them carries a character of the text.
    assertThat(queries).anyMatch(q -> q.contains("array_position"));
    assertThat(queries).anyMatch(q -> q.contains(".industry)"));
    assertThat(queries.stream().filter(q -> q.contains("Brien") || q.contains("brien"))).isEmpty();
  }

  /** The ids of the records the list returns for the filter. */
  private List<Long> ids(String token, long w, Listing l, String filter) throws Exception {
    List<Long> ids = new ArrayList<>();
    list(token, w, l, filter).get("content").forEach(r -> ids.add(r.get("id").asLong()));
    return ids;
  }

  private List<String> names(String token, long w, Listing l, String filter) throws Exception {
    List<String> names = new ArrayList<>();
    list(token, w, l, filter).get("content").forEach(r -> names.add(r.get(l.field()).asText()));
    return names;
  }

  private JsonNode list(String token, long w, Listing l, String filter) throws Exception {
    return body(send(token, filtered(get(W + l.path(), w), filter)).andExpect(status().isOk()));
  }

  /** The ids in the export's rows, from their references (first column, {@code P-0042}). */
  private List<Long> exported(String token, long w, Listing l, String filter) throws Exception {
    String csv =
        send(token, filtered(get(W + l.path() + "/export", w), filter))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Long> ids = new ArrayList<>();
    String[] lines = csv.substring(1).split("\r\n");
    for (int i = 1; i < lines.length; i++) {
      assertThat(lines[i]).startsWith(l.ref() + "-");
      ids.add(Long.parseLong(lines[i].substring(2, lines[i].indexOf(','))));
    }
    return ids;
  }

  /** {@code ""}: the text goes to q; else to the parameter of that name. */
  private static MockHttpServletRequestBuilder filtered(
      MockHttpServletRequestBuilder request, String filter) {
    return request.param(filter.isEmpty() ? "q" : filter, NEEDLE);
  }

  private void create(
      String token, long w, Listing l, String field, String value, Map<String, Object> extra)
      throws Exception {
    Map<String, Object> body = new HashMap<>(l.more());
    body.putAll(extra);
    body.put(field, value);
    send(token, post(W + l.path(), w), body).andExpect(status().isCreated());
  }
}
