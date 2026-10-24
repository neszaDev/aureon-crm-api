package com.example.boilerplate.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

  /** One statement: concurrent clean-ups that hit the same rows don't conflict (no 409). */
  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :time")
  int deleteByExpiresAtBefore(@Param("time") Instant time);

  @Modifying
  @Query("delete from RefreshToken t where t.familyId = :familyId")
  int deleteByFamilyId(@Param("familyId") UUID familyId);

  @Modifying
  @Query("delete from RefreshToken t where t.user.id = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
