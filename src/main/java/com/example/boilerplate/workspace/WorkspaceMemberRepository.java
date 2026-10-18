package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.MemberRef;
import com.example.boilerplate.workspace.dto.MemberResponse;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
      where m.workspaceId = :workspaceId and m.userId = :userId""")
  Optional<Membership> findMembership(long workspaceId, long userId);

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(long workspaceId, long userId);

  /** Who of {@code userIds} is a member of the workspace; one row per member. */
  @Query(
      """
      select new com.example.boilerplate.workspace.dto.MemberRef(u.id, u.email, u.displayName)
      from WorkspaceMember m
      join User u on u.id = m.userId
      where m.workspaceId = :workspaceId and m.userId in :userIds""")
  List<MemberRef> findRefs(long workspaceId, Collection<Long> userIds);

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
   * waits and then no longer sees the owner the first one demoted or removed. Ordered by id, so
   * concurrent transactions lock in one consistent order and can't deadlock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select m from WorkspaceMember m
      where m.workspaceId = :workspaceId
        and m.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER
      order by m.id""")
  List<WorkspaceMember> lockOwners(long workspaceId);

  /** Locks the owner rows of every workspace {@code userId} owns, as {@link #lockOwners} does. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select o from WorkspaceMember o
      where o.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER
        and o.workspaceId in (
          select m.workspaceId from WorkspaceMember m
          where m.userId = :userId
            and m.role = com.example.boilerplate.workspace.WorkspaceRole.OWNER)
      order by o.id""")
  List<WorkspaceMember> lockOwnersOfWorkspacesOwnedBy(long userId);
}
