package com.example.boilerplate.testresult;

import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.testresult.dto.*;
import com.example.boilerplate.user.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TestResultService {
  private final TestResultRepository results;
  private final UserRepository users;

  public TestResultService(TestResultRepository results, UserRepository users) {
    this.results = results;
    this.users = users;
  }

  public TestResultResponse create(String email, CreateTestResultRequest r) {
    User user = user(email);
    return response(
        results.save(
            new TestResult(user, r.testName(), r.status(), r.score(), r.testedAt(), r.notes())));
  }

  @Transactional(readOnly = true)
  public Page<TestResultResponse> list(String email, int page, int size) {
    return results
        .findByOwnerEmail(
            email, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "testedAt")))
        .map(this::response);
  }

  @Transactional(readOnly = true)
  public TestResultResponse get(String email, Long id) {
    return response(result(email, id));
  }

  public TestResultResponse update(String email, Long id, UpdateTestResultRequest r) {
    TestResult result = result(email, id);
    result.update(r.testName(), r.status(), r.score(), r.testedAt(), r.notes());
    return response(result);
  }

  public void delete(String email, Long id) {
    results.delete(result(email, id));
  }

  @Transactional(readOnly = true)
  public TestResultSummaryResponse summary(String email) {
    List<StatusCount> values =
        Arrays.stream(TestStatus.values())
            .map(s -> new StatusCount(s, results.countByOwnerEmailAndStatus(email, s)))
            .toList();
    return new TestResultSummaryResponse(
        values.stream().mapToLong(StatusCount::count).sum(), values);
  }

  private User user(String email) {
    return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private TestResult result(String email, Long id) {
    return results
        .findByIdAndOwnerEmail(id, email)
        .orElseThrow(() -> new NotFoundException("Test result not found"));
  }

  private TestResultResponse response(TestResult r) {
    return new TestResultResponse(
        r.getId(),
        r.getTestName(),
        r.getStatus(),
        r.getScore(),
        r.getTestedAt(),
        r.getNotes(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
