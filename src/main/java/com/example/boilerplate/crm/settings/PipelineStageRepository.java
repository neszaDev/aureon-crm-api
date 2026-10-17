package com.example.boilerplate.crm.settings;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {
  List<PipelineStage> findAllByOrderByPositionAscIdAsc();

  boolean existsByNameIgnoreCaseAndIdNot(String name, long id);

  /**
   * Locks the workspace's stages until the transaction ends, in id order so that two lockers can't
   * deadlock: deletes and reorders of one pipeline run one after the other, each on what the
   * previous one left.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PipelineStage s order by s.id")
  List<PipelineStage> lockAll();
}
