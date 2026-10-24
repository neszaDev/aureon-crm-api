package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.crm.settings.LeadSource;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import com.example.boilerplate.crm.settings.PipelineStage;
import com.example.boilerplate.crm.settings.PipelineStageRepository;
import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Hibernate's {@code @TenantId} keeps every workspace-owned query inside the current workspace. */
class TenantIsolationIT extends WorkspaceTestSupport {
  static final String STAGES = "/api/v1/workspaces/{workspaceId}/pipeline-stages";
  static final String SOURCES = "/api/v1/workspaces/{workspaceId}/lead-sources";

  @Autowired PipelineStageRepository stages;
  @Autowired LeadSourceRepository sources;

  @Test
  void stageIdOfAnotherWorkspaceIs404() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long stageOfB = body(send(b, get(STAGES, wb))).get(0).get("id").asLong();

    send(
            a,
            put(STAGES + "/{id}", wa, stageOfB),
            Map.of("name", "Hijacked", "probability", 0, "version", 0))
        .andExpect(status().isNotFound());
    send(a, delete(STAGES + "/{id}", wa, stageOfB)).andExpect(status().isNotFound());

    send(b, get(STAGES, wb))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].id").value(stageOfB))
        .andExpect(jsonPath("$[0].name").value("Qualification"))
        .andExpect(jsonPath("$[0].version").value(0));
  }

  @Test
  void sameForLeadSources() throws Exception {
    String a = tokenFor(uniqueEmail());
    long wa = createWorkspace(a, "A");
    String b = tokenFor(uniqueEmail());
    long wb = createWorkspace(b, "B");
    long sourceOfB = body(send(b, get(SOURCES, wb))).get(0).get("id").asLong();

    send(
            a,
            put(SOURCES + "/{id}", wa, sourceOfB),
            Map.of("name", "Hijacked", "archived", true, "version", 0))
        .andExpect(status().isNotFound());
    send(a, delete(SOURCES + "/{id}", wa, sourceOfB)).andExpect(status().isNotFound());

    send(b, get(SOURCES, wb))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].id").value(sourceOfB))
        .andExpect(jsonPath("$[0].name").value("Event"))
        .andExpect(jsonPath("$[0].archived").value(false))
        .andExpect(jsonPath("$[0].version").value(0));
  }

  @Test
  void queriesWithoutWorkspaceContextReturnNothing() throws Exception {
    String emailA = uniqueEmail();
    long wa = createWorkspace(tokenFor(emailA), "A");
    String emailB = uniqueEmail();
    long wb = createWorkspace(tokenFor(emailB), "B");
    Membership memberOfA = membership(wa, emailA);
    Membership memberOfB = membership(wb, emailB);

    assertThat(stages.findAll()).isEmpty();

    List<PipelineStage> ofA = WorkspaceContext.runAs(memberOfA, () -> stages.findAll());
    assertThat(ofA).hasSize(6).allSatisfy(s -> assertThat(s.getWorkspaceId()).isEqualTo(wa));
    // Load by id is filtered too: outside a context, and from another workspace.
    long stageOfA = ofA.get(0).getId();
    assertThat(stages.findById(stageOfA)).isEmpty();
    assertThat(WorkspaceContext.runAs(memberOfB, () -> stages.findById(stageOfA))).isEmpty();
    assertThat(WorkspaceContext.runAs(memberOfA, () -> stages.findById(stageOfA))).isPresent();
  }

  @Test
  void insertWithoutContextFails() {
    assertThatThrownBy(() -> sources.save(new LeadSource("Orphan")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
