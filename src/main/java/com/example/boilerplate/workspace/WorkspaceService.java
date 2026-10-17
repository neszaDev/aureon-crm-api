package com.example.boilerplate.workspace;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.settings.WorkspaceDefaults;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.dto.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceService {
  private final WorkspaceRepository workspaces;
  private final WorkspaceMemberRepository members;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;
  private final WorkspaceDefaults defaults;

  public WorkspaceService(
      WorkspaceRepository workspaces,
      WorkspaceMemberRepository members,
      UserRepository users,
      WorkspaceAccess access,
      AuditTrail audit,
      AuditChanges auditChanges,
      WorkspaceDefaults defaults) {
    this.workspaces = workspaces;
    this.members = members;
    this.users = users;
    this.access = access;
    this.audit = audit;
    this.auditChanges = auditChanges;
    this.defaults = defaults;
  }

  @Transactional(readOnly = true)
  public List<WorkspaceSummaryResponse> mine(String email) {
    return workspaces.findSummariesByMemberEmail(email);
  }

  /** The workspace, its owner membership and its default stages and sources commit together. */
  public WorkspaceResponse create(String email, CreateWorkspaceRequest r) {
    User user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    Workspace w =
        workspaces.save(
            new Workspace(Texts.required("name", r.name()), r.defaultCurrency(), r.timezone()));
    members.save(new WorkspaceMember(w.getId(), user.getId(), WorkspaceRole.OWNER));
    defaults.seed(w.getId());
    // No workspace context yet: the workspace is being created.
    audit.record(
        w.getId(),
        user.getId(),
        user.getEmail(),
        AuditAction.CREATE,
        AuditEntityType.WORKSPACE,
        w.getId(),
        w.getName(),
        Map.of());
    return response(w, WorkspaceRole.OWNER);
  }

  @Transactional(readOnly = true)
  public WorkspaceResponse get() {
    Membership m = WorkspaceContext.require();
    return response(workspace(m.workspaceId()), m.role());
  }

  public WorkspaceResponse update(UpdateWorkspaceRequest r) {
    Membership m = access.require(Permission.WORKSPACE_MANAGE);
    Workspace w = workspace(m.workspaceId());
    Versions.check(w, r.version());
    WorkspaceResponse before = response(w, m.role());
    w.update(Texts.required("name", r.name()), r.defaultCurrency(), r.timezone());
    // Flush so the response carries the new version and updatedAt.
    WorkspaceResponse after = response(workspaces.saveAndFlush(w), m.role());
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.WORKSPACE, w.getId(), w.getName(), changes);
    }
    return after;
  }

  private Workspace workspace(long id) {
    return workspaces.findById(id).orElseThrow(() -> new NotFoundException("Workspace not found"));
  }

  private static WorkspaceResponse response(Workspace w, WorkspaceRole role) {
    return new WorkspaceResponse(
        w.getId(),
        w.getName(),
        w.getDefaultCurrency(),
        w.getTimezone(),
        role,
        List.copyOf(role.permissions()),
        w.getVersion(),
        w.getCreatedAt(),
        w.getUpdatedAt());
  }
}
