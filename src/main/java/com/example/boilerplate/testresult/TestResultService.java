package com.example.boilerplate.testresult;

import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.web.Paging;
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

  public TestResultResponse create(long ownerId, CreateTestResultRequest r) {
    User user = user(ownerId);
    return response(
        results.save(
            new TestResult(user, r.testName(), r.status(), r.score(), r.testedAt(), r.notes())));
  }

  @Transactional(readOnly = true)
  public Page<TestResultResponse> list(long ownerId, int page, int size) {
    return results
        .findByOwnerId(
            ownerId, Paging.request(page, size, Sort.by(Sort.Direction.DESC, "testedAt")))
        .map(this::response);
  }

  @Transactional(readOnly = true)
  public TestResultResponse get(long ownerId, Long id) {
    return response(result(ownerId, id));
  }

  public TestResultResponse update(long ownerId, Long id, UpdateTestResultRequest r) {
    TestResult result = result(ownerId, id);
    result.update(r.testName(), r.status(), r.score(), r.testedAt(), r.notes());
    return response(result);
  }

  public void delete(long ownerId, Long id) {
    results.delete(result(ownerId, id));
  }

  @Transactional(readOnly = true)
  public TestResultSummaryResponse summary(long ownerId) {
    List<StatusCount> values =
        Arrays.stream(TestStatus.values())
            .map(s -> new StatusCount(s, results.countByOwnerIdAndStatus(ownerId, s)))
            .toList();
    return new TestResultSummaryResponse(
        values.stream().mapToLong(StatusCount::count).sum(), values);
  }

  private User user(long ownerId) {
    return users.findById(ownerId).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private TestResult result(long ownerId, Long id) {
    return results
        .findByIdAndOwnerId(id, ownerId)
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
