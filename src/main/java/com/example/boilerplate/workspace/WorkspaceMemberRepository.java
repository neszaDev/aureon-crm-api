package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.MemberResponse;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
  @Query(
      """
      select new com.example.boilerplate.workspace.Membership(m.workspaceId, u.id, u.email, m.role)
      from WorkspaceMember m
      join User u on u.id = m.userId
      where m.workspaceId = :workspaceId and u.email = :email""")
  Optional<Membership> findMembership(long workspaceId, String email);

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(long workspaceId, long userId);

  boolean existsByWorkspaceIdAndUserId(long workspaceId, long userId);

  @Query(
      """
      select new com.example.boilerplate.workspace.dto.MemberResponse(
        u.id, u.email, u.displayName, m.role, m.createdAt)
      from WorkspaceMember m
      join User u on u.id = m.userId
      where m.workspaceId = :workspaceId
      order by lower(u.displayName) nulls last, u.email""")
  List<MemberResponse> list(long workspaceId);

  @Query(
      """
      select count(m) > 0 from WorkspaceMember m join User u on u.id = m.userId
      where m.workspaceId = :workspaceId and u.email = :email""")
  boolean existsByWorkspaceIdAndEmail(long workspaceId, String email);

  /**
   * Locks the owners' rows, so two requests can't each remove "the other" owner: the second one
   * waits and then no longer sees the owner the first one demoted or removed.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select m from WorkspaceMember m
      where m.workspaceId = :workspaceId
        and m.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER""")
  List<WorkspaceMember> lockOwners(long workspaceId);

  /** Whether the user is an owner of a workspace that has no other owner. */
  @Query(
      """
      select count(m) > 0 from WorkspaceMember m
      where m.userId = :userId
        and m.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER
        and not exists (
          select 1 from WorkspaceMember o
          where o.workspaceId = m.workspaceId
            and o.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER
            and o.userId <> :userId)""")
  boolean isSoleOwnerOfAWorkspace(long userId);
}
