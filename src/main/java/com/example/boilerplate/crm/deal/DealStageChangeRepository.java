package com.example.boilerplate.crm.deal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped to the current workspace by {@code @TenantId}. */
public interface DealStageChangeRepository extends JpaRepository<DealStageChange, Long> {
  List<DealStageChange> findByDealIdOrderByChangedAtDescIdDesc(long dealId);
}
