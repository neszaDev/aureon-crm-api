package com.example.boilerplate.testresult;

import com.example.boilerplate.testresult.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/test-results")
public class TestResultController {
  private final TestResultService service;

  public TestResultController(TestResultService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TestResultResponse create(Authentication a, @Valid @RequestBody CreateTestResultRequest r) {
    return service.create(a.getName(), r);
  }

  @GetMapping
  Page<TestResultResponse> list(
      Authentication a,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(a.getName(), page, size);
  }

  @GetMapping("/summary")
  TestResultSummaryResponse summary(Authentication a) {
    return service.summary(a.getName());
  }

  @GetMapping("/{id}")
  TestResultResponse get(Authentication a, @PathVariable Long id) {
    return service.get(a.getName(), id);
  }

  @PatchMapping("/{id}")
  TestResultResponse update(
      Authentication a, @PathVariable Long id, @Valid @RequestBody UpdateTestResultRequest r) {
    return service.update(a.getName(), id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(Authentication a, @PathVariable Long id) {
    service.delete(a.getName(), id);
  }
}
