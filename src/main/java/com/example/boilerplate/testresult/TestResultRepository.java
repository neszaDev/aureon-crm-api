package com.example.boilerplate.testresult;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
  Page<TestResult> findByOwnerId(long ownerId, Pageable pageable);

  Optional<TestResult> findByIdAndOwnerId(Long id, long ownerId);

  long countByOwnerIdAndStatus(long ownerId, TestStatus status);
}
