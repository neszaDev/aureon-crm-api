package com.example.boilerplate.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AuditTrailIT extends WorkspaceTestSupport {
  @Autowired AuditEventRepository events;
  @Autowired AuditTrail audit;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void workspaceCreateAndUpdateAreAudited() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long id = createWorkspace(token, "Acme");
    String requestId = "req-" + UUID.randomUUID();

    rename(token, id, "Renamed", 0L, requestId);

    List<AuditEvent> trail = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(id);
    assertThat(trail).hasSize(2);
    AuditEvent update = trail.get(0);
    assertThat(update.getAction()).isEqualTo(AuditAction.UPDATE);
    assertThat(update.getEntityType()).isEqualTo(AuditEntityType.WORKSPACE);
    assertThat(update.getEntityId()).isEqualTo(id);
    assertThat(update.getEntityLabel()).isEqualTo("Renamed");
    assertThat(update.getActorEmail()).isEqualTo(email);
    assertThat(update.getActorId()).isEqualTo(users.findByEmail(email).orElseThrow().getId());
    assertThat(update.getRequestId()).isEqualTo(requestId);
    // Only what the caller edited: not the version or updatedAt that change with every edit.
    assertThat(update.getChanges())
        .containsOnlyKeys("name")
        .containsEntry("name", Map.of("from", "Acme", "to", "Renamed"));
    AuditEvent create = trail.get(1);
    assertThat(create.getAction()).isEqualTo(AuditAction.CREATE);
    assertThat(create.getEntityType()).isEqualTo(AuditEntityType.WORKSPACE);
    assertThat(create.getEntityId()).isEqualTo(id);
    assertThat(create.getEntityLabel()).isEqualTo("Acme");
    assertThat(create.getActorEmail()).isEqualTo(email);
    assertThat(create.getChanges()).isEmpty();
    assertThat(create.getOccurredAt()).isNotNull();
  }

  @Test
  void updateThatChangesNothingIsNotAudited() throws Exception {
    String token = tokenFor(uniqueEmail());
    long id = createWorkspace(token, "Acme");

    rename(token, id, "Acme", 0L, "req-" + UUID.randomUUID());

    assertThat(events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(id))
        .extracting(AuditEvent::getAction)
        .containsExactly(AuditAction.CREATE);
  }

  @Test
  void auditEventsAreAppendOnly() throws Exception {
    long workspaceId = createWorkspace(tokenFor(uniqueEmail()), "Acme");
    long eventId = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(workspaceId).get(0).getId();

    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_events SET action = 'X' WHERE id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");

    assertThat(events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(workspaceId))
        .extracting(AuditEvent::getId)
        .contains(eventId);
  }

  @Test
  void recordRefusesToRunOutsideATransaction() {
    assertThatThrownBy(
            () ->
                audit.record(
                    1L,
                    1L,
                    "a@example.com",
                    AuditAction.CREATE,
                    AuditEntityType.COMPANY,
                    1L,
                    "Acme",
                    Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void overlongLabelAndRequestIdAreTruncatedAndChangesKeepTheirShape() throws Exception {
    long workspaceId = createWorkspace(tokenFor(uniqueEmail()), "Acme");
    Map<String, Map<String, Object>> changes =
        AuditChanges.diff(Map.of("stage", "Lead"), Map.of("stage", "Won", "amount", 5));

    MDC.put("requestId", "r".repeat(100));
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s ->
                  audit.record(
                      workspaceId,
                      7L,
                      "rep@example.com",
                      AuditAction.STAGE_CHANGE,
                      AuditEntityType.DEAL,
                      9L,
                      "x".repeat(300),
                      changes));
    } finally {
      MDC.remove("requestId");
    }

    AuditEvent e = events.findByWorkspaceIdOrderByOccurredAtDescIdDesc(workspaceId).get(0);
    assertThat(e.getAction()).isEqualTo(AuditAction.STAGE_CHANGE);
    assertThat(e.getEntityLabel()).isEqualTo("x".repeat(200));
    assertThat(e.getRequestId()).isEqualTo("r".repeat(64));
    assertThat(e.getChanges())
        .containsEntry("stage", Map.of("from", "Lead", "to", "Won"))
        .containsKey("amount");
    assertThat(e.getChanges().get("amount")).isEqualTo(changes.get("amount"));
  }

  private void rename(String token, long id, String name, long version, String requestId)
      throws Exception {
    mvc.perform(
            put("/api/v1/workspaces/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            name,
                            "defaultCurrency",
                            "EUR",
                            "timezone",
                            "Europe/Paris",
                            "version",
                            version))))
        .andExpect(status().isOk());
  }
}
