package com.example.boilerplate.workspace;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
