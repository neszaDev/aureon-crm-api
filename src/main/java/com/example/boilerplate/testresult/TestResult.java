package com.example.boilerplate.testresult;

import com.example.boilerplate.common.entity.AuditableEntity; import com.example.boilerplate.user.User;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name = "test_results")
public class TestResult extends AuditableEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private User owner;
  @Column(nullable = false, length = 100) private String testName;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TestStatus status;
  @Column(nullable = false, precision = 8, scale = 2) private BigDecimal score;
  @Column(nullable = false) private Instant testedAt;
  @Column(length = 1000) private String notes;
  protected TestResult() {}
  public TestResult(User owner,String testName,TestStatus status,BigDecimal score,Instant testedAt,String notes) { this.owner=owner; apply(testName,status,score,testedAt,notes); }
  public void update(String testName,TestStatus status,BigDecimal score,Instant testedAt,String notes) { apply(testName,status,score,testedAt,notes); }
  // Private so the constructor does not call an overridable method (javac this-escape).
  private void apply(String testName,TestStatus status,BigDecimal score,Instant testedAt,String notes) { this.testName=testName;this.status=status;this.score=score;this.testedAt=testedAt;this.notes=notes; }
  public String getTestName(){return testName;} public TestStatus getStatus(){return status;} public BigDecimal getScore(){return score;} public Instant getTestedAt(){return testedAt;} public String getNotes(){return notes;}
}
