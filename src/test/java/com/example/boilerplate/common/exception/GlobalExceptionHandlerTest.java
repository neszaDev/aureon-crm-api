package com.example.boilerplate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

/** How a database error that Spring reports as an integrity violation reaches the client. */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
  private static final String ROUTE = "/api/v1/workspaces/{workspaceId}/contacts";

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /** As Hibernate and Spring wrap it: the PostgreSQL error is the most specific cause. */
  private ResponseEntity<ApiError> answer(String sqlState, String message) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/workspaces/1/x");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ROUTE);
    SQLException sql = new SQLException(message, sqlState);
    return handler.integrity(
        new DataIntegrityViolationException("could not execute statement", new Exception(sql)),
        request);
  }

  private ResponseEntity<ApiError> answer(String sqlState) {
    return answer(sqlState, "ERROR from PostgreSQL");
  }

  @Test
  void aValueTooLongOrOutOfRangeIsTheClientsMistake() {
    // Too long, out of range, untranslatable; a date or time the database can't hold.
    for (String state : new String[] {"22001", "22003", "22P05", "22007", "22008"}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(r.getBody().message()).as(state).isEqualTo("Invalid value");
      assertThat(r.getBody().code()).as(state).isEqualTo(ErrorCode.TEXT_INVALID);
    }
  }

  @Test
  void aDataExceptionLogsItsStateAndRouteButNotTheValue(CapturedOutput output) {
    answer("22001", "value too long for type character varying(100): secret-value");

    assertThat(output).contains("Data exception 22001 on POST " + ROUTE);
    assertThat(output).doesNotContain("secret-value");
  }

  @Test
  void aNullCharacterKeepsItsOwnMessage() {
    ResponseEntity<ApiError> r = answer("22021");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(r.getBody().message()).isEqualTo("Text must not contain null characters");
    assertThat(r.getBody().code()).isEqualTo(ErrorCode.TEXT_INVALID);
  }

  @Test
  void otherDataExceptionsAreServerBugs() {
    // Division by zero, invalid regex, invalid text representation.
    for (String state : new String[] {"22012", "2201B", "22P02"}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(r.getBody().message()).as(state).isEqualTo("Unexpected server error");
      assertThat(r.getBody().code()).as(state).isNull();
    }
  }

  /** Its messages can quote stored values: the classes of the chain and the SQLSTATE only. */
  @Test
  void aServerErrorFromTheDatabaseLogsItsClassesAndStateButNoMessage(CapturedOutput output) {
    answer("22P02", "invalid input syntax for type integer: \"secret-value\"");

    assertThat(output)
        .contains(
            "Unhandled exception on POST "
                + ROUTE
                + ": SQLSTATE 22P02 in org.springframework.dao.DataIntegrityViolationException"
                + " < java.lang.Exception < java.sql.SQLException")
        .doesNotContain("secret-value")
        .doesNotContain("could not execute statement");
  }

  /** Any other 500 is a bug in the code: logged with its message and stack. */
  @Test
  void anyOtherServerErrorLogsTheWholeException(CapturedOutput output) {
    handler.generic(
        new IllegalStateException("bug detail"), new MockHttpServletRequest("GET", "/x"));

    assertThat(output)
        .contains("Unhandled exception on GET <unmatched>")
        .contains("java.lang.IllegalStateException: bug detail")
        .contains("at " + GlobalExceptionHandlerTest.class.getName());
  }

  @Test
  void aConstraintViolationIsStillAConflict() {
    for (String state : new String[] {"23505", "23503", null}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.CONFLICT);
      assertThat(r.getBody().message()).isEqualTo("Conflicts with existing data");
      assertThat(r.getBody().code()).as(state).isEqualTo(ErrorCode.CONFLICT);
    }
  }

  /**
   * A unique index or foreign key behind a rule the service checks first: the race loser gets the
   * rule's code. The foreign keys' "in use" is the delete's; a create or edit that meets one lost
   * its stage or source to that delete: a plain conflict.
   */
  @Test
  void aConstraintOfARuleAnswersWithItsCode() {
    Map<String, ErrorCode> unique =
        Map.of(
            "uq_pipeline_stages_name", ErrorCode.DUPLICATE_NAME,
            "uq_lead_sources_name", ErrorCode.DUPLICATE_NAME,
            "users_email_key", ErrorCode.EMAIL_TAKEN,
            "uq_workspace_invitations_pending", ErrorCode.ALREADY_INVITED,
            "deals_workspace_id_lead_id_key", ErrorCode.CONFLICT);
    unique.forEach((constraint, code) -> assertCode(constraint, "POST", code));
    Map<String, ErrorCode> inUse =
        Map.of(
            "deals_workspace_id_stage_id_fkey", ErrorCode.STAGE_IN_USE,
            "leads_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE,
            "contacts_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE,
            "deals_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE);
    inUse.forEach(
        (constraint, code) -> {
          assertCode(constraint, "DELETE", code);
          assertCode(constraint, "POST", ErrorCode.CONFLICT);
          assertCode(constraint, "PUT", ErrorCode.CONFLICT);
        });
  }

  private void assertCode(String constraint, String method, ErrorCode code) {
    SQLException sql = new SQLException("violation", "23505");
    ResponseEntity<ApiError> r =
        handler.integrity(
            new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("statement", sql, constraint)),
            new MockHttpServletRequest(method, "/x"));
    assertThat(r.getStatusCode()).as(method + " " + constraint).isEqualTo(HttpStatus.CONFLICT);
    assertThat(r.getBody().code()).as(method + " " + constraint).isEqualTo(code);
  }

  /**
   * A stale version, and a lock the database took back (PostgreSQL aborts one side of a deadlock,
   * 40P01, which Spring reports as a pessimistic locking failure): the same answer, so the client
   * reloads and retries. Through MockMvc, so the handler is picked by the exception's type.
   */
  @Test
  void aConcurrentUpdateIsAVersionConflict() throws Exception {
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new Failing()).setControllerAdvice(handler).build();
    for (String kind : new String[] {"stale", "lock", "timeout"}) {
      mvc.perform(get("/fail/{kind}", kind))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
          .andExpect(
              jsonPath("$.message").value("Changed by another request; reload and try again"));
    }
  }

  @RestController
  static class Failing {
    @GetMapping("/fail/{kind}")
    void fail(@PathVariable String kind) {
      throw switch (kind) {
        case "stale" -> new OptimisticLockingFailureException("stale");
        case "lock" -> new PessimisticLockingFailureException("lock");
        default -> new CannotAcquireLockException("lock timeout");
      };
    }
  }
}
