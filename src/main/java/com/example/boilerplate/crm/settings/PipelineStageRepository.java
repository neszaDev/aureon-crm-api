package com.example.boilerplate.crm.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {
  List<PipelineStage> findAllByOrderByPositionAscIdAsc();

  boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
