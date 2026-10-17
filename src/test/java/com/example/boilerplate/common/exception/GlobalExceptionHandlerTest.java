package com.example.boilerplate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/** How a database error that Spring reports as an integrity violation reaches the client. */
class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");

  /** As Hibernate and Spring wrap it: the PostgreSQL error is the most specific cause. */
  private ResponseEntity<ApiError> answer(String sqlState) {
    SQLException sql = new SQLException("ERROR from PostgreSQL", sqlState);
    return handler.integrity(
        new DataIntegrityViolationException("could not execute statement", new Exception(sql)),
        request);
  }

  @Test
  void aDataExceptionIsTheClientsMistake() {
    // 22001: value too long, 22003: numeric value out of range.
    for (String state : new String[] {"22001", "22003", "22P02"}) {
      ResponseEntity<ApiError> r = answer(state);
      assertThat(r.getStatusCode()).as(state).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(r.getBody().message()).as(state).isEqualTo("Invalid value");
    }
  }

  @Test
  void aNullCharacterKeepsItsOwnMessage() {
    ResponseEntity<ApiError> r = answer("22021");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(r.getBody().message()).isEqualTo("Text must not contain null characters");
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
