package com.example.boilerplate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
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
    for (String state : new String[] {"22001", "22003", "22P05"}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(r.getBody().message()).as(state).isEqualTo("Invalid value");
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
  }

  @Test
  void otherDataExceptionsAreServerBugs() {
    // Division by zero, invalid regex, invalid text representation.
    for (String state : new String[] {"22012", "2201B", "22P02"}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(r.getBody().message()).as(state).isEqualTo("Unexpected server error");
    }
  }

  @Test
  void aConstraintViolationIsStillAConflict() {
    for (String state : new String[] {"23505", "23503", null}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.CONFLICT);
      assertThat(r.getBody().message()).isEqualTo("Conflicts with existing data");
    }
  }
}
