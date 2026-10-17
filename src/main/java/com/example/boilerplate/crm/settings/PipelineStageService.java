package com.example.boilerplate.crm.settings;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.settings.dto.*;
import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceAccess;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current workspace's pipeline. Positions are always 1..n: the open stages in their order, then
 * Won, then Lost.
 */
@Service
@Transactional
public class PipelineStageService {
  private final PipelineStageRepository stages;
  private final WorkspaceAccess access;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public PipelineStageService(
      PipelineStageRepository stages,
      WorkspaceAccess access,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.stages = stages;
    this.access = access;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  @Transactional(readOnly = true)
  public List<StageResponse> list() {
    return stages.findAllByOrderByPositionAscIdAsc().stream()
        .map(PipelineStageService::response)
        .toList();
  }

  /** A new stage is open and goes after the open ones; Won and Lost move down. */
  public StageResponse create(CreateStageRequest r) {
    access.require(Permission.SETTINGS_MANAGE);
    PipelineStage stage = new PipelineStage(uniqueName(r.name(), 0), r.probability());
    List<PipelineStage> all = stages.findAllByOrderByPositionAscIdAsc();
    List<PipelineStage> open = new ArrayList<>(open(all));
    open.add(stage);
    renumber(open, all);
    stages.save(stage);
    audit.record(
        AuditAction.CREATE,
        AuditEntityType.PIPELINE_STAGE,
        stage.getId(),
        stage.getName(),
        Map.of());
    return response(stage);
  }

  public StageResponse update(long id, UpdateStageRequest r) {
    access.require(Permission.SETTINGS_MANAGE);
    PipelineStage stage = stage(id);
    Versions.check(stage, r.version());
    String name = uniqueName(r.name(), id);
    Map<String, Object> before = auditChanges.snapshot(response(stage));
    stage.update(name, r.probability());
    // Flush so the response carries the new version.
    StageResponse after = response(stages.saveAndFlush(stage));
    auditUpdate(before, after);
    return after;
  }

  public List<StageResponse> reorder(ReorderStagesRequest r) {
    access.require(Permission.SETTINGS_MANAGE);
    List<PipelineStage> all = stages.findAllByOrderByPositionAscIdAsc();
    Map<Long, PipelineStage> open =
        open(all).stream().collect(Collectors.toMap(PipelineStage::getId, Function.identity()));
    List<Long> ids = r.stageIds();
    // Same size and same set: no stage missing, none twice, no closed or unknown id.
    if (ids.size() != open.size() || !open.keySet().equals(new HashSet<>(ids))) {
      throw new FieldValidationException("stageIds", "must list every open stage exactly once");
    }
    Map<Long, Map<String, Object>> before = new HashMap<>();
    for (PipelineStage s : all) before.put(s.getId(), auditChanges.snapshot(response(s)));
    List<PipelineStage> ordered = renumber(ids.stream().map(open::get).toList(), all);
    stages.flush();
    List<StageResponse> after = ordered.stream().map(PipelineStageService::response).toList();
    for (StageResponse s : after) auditUpdate(before.get(s.id()), s);
    return after;
  }

  public void delete(long id) {
    access.require(Permission.SETTINGS_MANAGE);
    PipelineStage stage = stage(id);
    if (stage.getKind() != StageKind.OPEN) {
      throw new ConflictException("The Won and Lost stages can't be deleted");
    }
    List<PipelineStage> all = stages.findAllByOrderByPositionAscIdAsc();
    List<PipelineStage> open = open(all);
    if (open.size() == 1) throw new ConflictException("The pipeline needs an open stage");
    // B2 adds: 409 while a deal is in this stage.
    stages.delete(stage);
    renumber(open.stream().filter(s -> s != stage).toList(), all);
    audit.record(AuditAction.DELETE, AuditEntityType.PIPELINE_STAGE, id, stage.getName(), Map.of());
  }

  private PipelineStage stage(long id) {
    return stages.findById(id).orElseThrow(() -> new NotFoundException("Stage not found"));
  }

  /** The name without blanks around it, unless another stage has it (any case); 0: a new one. */
  private String uniqueName(String name, long exceptId) {
    String stripped = Texts.required("name", name);
    if (stages.existsByNameIgnoreCaseAndIdNot(stripped, exceptId)) {
      throw new ConflictException("A stage with this name already exists");
    }
    return stripped;
  }

  private void auditUpdate(Map<String, Object> before, StageResponse after) {
    Map<String, Map<String, Object>> changes =
        AuditChanges.diff(before, auditChanges.snapshot(after));
    // Every edit bumps it; it says nothing about what was edited.
    changes.remove("version");
    if (!changes.isEmpty()) {
      audit.record(
          AuditAction.UPDATE, AuditEntityType.PIPELINE_STAGE, after.id(), after.name(), changes);
    }
  }

  private static List<PipelineStage> open(List<PipelineStage> all) {
    return all.stream().filter(s -> s.getKind() == StageKind.OPEN).toList();
  }

  /** Positions 1..n for {@code open} in that order, then Won, then Lost; returns that order. */
  private static List<PipelineStage> renumber(List<PipelineStage> open, List<PipelineStage> all) {
    List<PipelineStage> ordered = new ArrayList<>(open);
    all.stream()
        .filter(s -> s.getKind() != StageKind.OPEN)
        .sorted(Comparator.comparing(PipelineStage::getKind))
        .forEach(ordered::add);
    for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i + 1);
    return ordered;
  }

  private static StageResponse response(PipelineStage s) {
    return new StageResponse(
        s.getId(), s.getName(), s.getPosition(), s.getProbability(), s.getKind(), s.getVersion());
  }
}
