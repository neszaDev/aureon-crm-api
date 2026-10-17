package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.common.CrmRecord;
import com.example.boilerplate.crm.common.Money;
import com.example.boilerplate.crm.company.Company;
import com.example.boilerplate.crm.settings.PipelineStage;
import com.example.boilerplate.crm.settings.StageKind;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "deals")
public class Deal extends CrmRecord {
  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "stage_id", nullable = false)
  private Long stageId;

  /** Read-only, for the status filter's join; {@link #moveTo} writes {@link #stageId}. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stage_id", insertable = false, updatable = false)
  private PipelineStage stage;

  /** The deal's own probability; null means its stage's. */
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer probability;

  private LocalDate expectedCloseDate;

  @Column(name = "company_id")
  private Long companyId;

  /**
   * Read-only, for the search's join on the company name; the service writes {@link #companyId}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  private Long contactId;

  private Long sourceId;

  // Set by the lead conversion.
  private Long leadId;

  @Column(length = 500)
  private String lostReason;

  private Instant closedAt;

  @Column(length = 5000)
  private String notes;

  protected Deal() {}

  /**
   * A new deal in {@code stageId}, an open stage (the caller checked); {@link #update} fills it.
   */
  public Deal(long stageId) {
    this.stageId = stageId;
  }

  /**
   * Every editable field but the owner ({@link #changeOwner}) and the stage ({@link #moveTo}); null
   * clears an optional one. The links are already checked, the currency resolved.
   */
  public void update(
      String name,
      BigDecimal amount,
      String currency,
      Integer probability,
      LocalDate expectedCloseDate,
      Long companyId,
      Long contactId,
      Long sourceId,
      String notes) {
    this.name = name;
    this.amount = Money.scale2(amount);
    this.currency = currency;
    this.probability = probability;
    this.expectedCloseDate = expectedCloseDate;
    this.companyId = companyId;
    this.contactId = contactId;
    this.sourceId = sourceId;
    this.notes = notes;
  }

  /**
   * Into {@code stageId} of kind {@code to}, from a stage of kind {@code from}. Entering WON or
   * LOST from another kind closes the deal now; an open stage reopens it. Only LOST keeps a reason,
   * which the caller has checked.
   */
  public void moveTo(long stageId, StageKind from, StageKind to, String lostReason) {
    this.stageId = stageId;
    if (to == StageKind.OPEN) closedAt = null;
    // Microseconds, as stored, so this answer equals a later read.
    else if (to != from) closedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    this.lostReason = to == StageKind.LOST ? lostReason : null;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Long getStageId() {
    return stageId;
  }

  public Integer getProbability() {
    return probability;
  }

  public LocalDate getExpectedCloseDate() {
    return expectedCloseDate;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public Long getContactId() {
    return contactId;
  }

  public Long getSourceId() {
    return sourceId;
  }

  public Long getLeadId() {
    return leadId;
  }

  public String getLostReason() {
    return lostReason;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public String getNotes() {
    return notes;
  }
}
