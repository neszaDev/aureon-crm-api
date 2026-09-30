package com.example.boilerplate.testresult;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
  Page<TestResult> findByOwnerEmail(String email, Pageable pageable);

  Optional<TestResult> findByIdAndOwnerEmail(Long id, String email);

  long countByOwnerEmailAndStatus(String email, TestStatus status);
}
