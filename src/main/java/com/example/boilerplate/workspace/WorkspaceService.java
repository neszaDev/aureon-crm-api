package com.example.boilerplate.workspace;

import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.dto.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceService {
  private final WorkspaceRepository workspaces;
  private final WorkspaceMemberRepository members;
  private final UserRepository users;
  private final WorkspaceAccess access;

  public WorkspaceService(
      WorkspaceRepository workspaces,
      WorkspaceMemberRepository members,
      UserRepository users,
      WorkspaceAccess access) {
    this.workspaces = workspaces;
    this.members = members;
    this.users = users;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<WorkspaceSummaryResponse> mine(String email) {
    return workspaces.findSummariesByMemberEmail(email);
  }

  /** The workspace and its owner membership commit together. */
  public WorkspaceResponse create(String email, CreateWorkspaceRequest r) {
    User user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    Workspace w =
        workspaces.save(new Workspace(r.name().strip(), r.defaultCurrency(), r.timezone()));
    members.save(new WorkspaceMember(w.getId(), user.getId(), WorkspaceRole.OWNER));
    seedDefaults(w.getId());
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
    w.update(r.name().strip(), r.defaultCurrency(), r.timezone());
    // Flush so the response carries the new version and updatedAt.
    return response(workspaces.saveAndFlush(w), m.role());
  }

  private void seedDefaults(long id) {
    // Task 4 adds WorkspaceDefaults.seed(id): default pipeline stages and lead sources.
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
