package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.common.exception.ApiError;
import com.example.boilerplate.common.exception.GlobalExceptionHandler;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finding S-2 (phase 4 security review, carried item #30): stored values reach the logs through
 * PostgreSQL's error messages. pgjdbc puts the server's Detail ("Key (email)=(...)", "Failing row
 * contains (...)") into the exception message; Hibernate's SqlExceptionHelper logs that message at
 * ERROR on every constraint violation, and GlobalExceptionHandler.generic logs the whole exception
 * chain on a 500. The handler's own lines (route and SQLSTATE) are clean.
 */
@ExtendWith(OutputCaptureExtension.class)
class S2DatabaseValuesInLogsProbeIT extends WorkspaceTestSupport {
  private static final String INVITATIONS = "/api/v1/workspaces/{workspaceId}/invitations";

  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate tx;

  /** A 409 over HTTP: the duplicated email ends up in the log (SqlExceptionHelper, ERROR). */
  @Test
  void aUniqueViolationLogsTheDuplicatedValue(CapturedOutput output) throws Exception {
    String owner = tokenFor(uniqueEmail());
    long w = createWorkspace(owner, "Acme");
    String email = uniqueEmail();
    tokenFor(email);
    JsonNode created =
        body(
            send(owner, post(INVITATIONS, w), Map.of("email", email, "role", "SALES_REP"))
                .andExpect(status().isCreated()));
    // As InvitationIT does: an upper-case copy passes the service's check, then users.email's
    // unique key refuses the lower-cased insert.
    jdbc.update(
        "UPDATE workspace_invitations SET email = upper(email) WHERE id = ?",
        created.get("id").asLong());
    mvc.perform(
            post("/api/v1/invitations/{t}/register", created.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", PASSWORD))))
        .andExpect(status().isConflict());

    assertThat(output.getAll()).contains("Constraint violation on POST");
    assertThat(output.getAll())
        .as("the stored email, from the server's Detail")
        .doesNotContain(email);
  }

  /**
   * A NOT NULL or CHECK violation through Hibernate: "Failing row contains (...)" logs the whole
   * row, here a users row with its password hash. (Inserted directly: no endpoint is known to reach
   * one; a server bug or a race would.)
   */
  @Test
  void aNotNullViolationLogsTheWholeRow(CapturedOutput output) {
    String hash = "$2a$10$" + UUID.randomUUID().toString().replace("-", "") + "markerhash";
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s ->
                        entityManager
                            .createNativeQuery(
                                "INSERT INTO users (email, password_hash, role) VALUES (?, ?, NULL)")
                            .setParameter(1, uniqueEmail())
                            .setParameter(2, hash)
                            .executeUpdate()))
        .isInstanceOf(RuntimeException.class);

    assertThat(output.getAll())
        .as("the password hash, from 'Failing row contains'")
        .doesNotContain(hash);
  }

  /**
   * A 500 (a data exception that isn't the client's): the handler logs the value with the chain.
   */
  @Test
  @SuppressWarnings("unchecked")
  void aServerErrorLogsTheValueInTheMessage(CapturedOutput output) throws Exception {
    String marker = "marker-" + UUID.randomUUID();
    DataAccessException e = null;
    try {
      jdbc.queryForObject("SELECT CAST(? AS integer)", Integer.class, marker);
    } catch (DataAccessException thrown) {
      e = thrown;
    }
    assertThat(e).isNotNull();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
    assertThat(e).isInstanceOf(DataIntegrityViolationException.class);
    // The handler's method is package-private; it is what the 500 path runs.
    Method integrity =
        GlobalExceptionHandler.class.getDeclaredMethod(
            "integrity", DataIntegrityViolationException.class, HttpServletRequest.class);
    integrity.setAccessible(true);
    var answer =
        (ResponseEntity<ApiError>) integrity.invoke(new GlobalExceptionHandler(), e, request);

    assertThat(answer.getStatusCode().value()).isEqualTo(500);
    assertThat(answer.getBody().message()).isEqualTo("Unexpected server error");
    assertThat(output.getAll()).as("the value, from PostgreSQL's message").doesNotContain(marker);
  }
}
