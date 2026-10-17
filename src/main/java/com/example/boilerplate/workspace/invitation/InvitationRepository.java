package com.example.boilerplate.workspace.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
  Optional<Invitation> findByTokenHash(String tokenHash);

  Optional<Invitation> findByIdAndWorkspaceId(long id, long workspaceId);

  /** Neither accepted nor revoked, expired or not: the one the partial unique index allows. */
  @Query(
      """
      select i from Invitation i
      where i.workspaceId = :workspaceId and i.email = :email
        and i.acceptedAt is null and i.revokedAt is null""")
  Optional<Invitation> findOpen(long workspaceId, String email);

  @Query(
      """
      select i from Invitation i left join fetch i.invitedBy
      where i.workspaceId = :workspaceId
        and i.acceptedAt is null and i.revokedAt is null and i.expiresAt > :now
      order by i.createdAt desc, i.id desc""")
  List<Invitation> findPending(long workspaceId, Instant now);
}
