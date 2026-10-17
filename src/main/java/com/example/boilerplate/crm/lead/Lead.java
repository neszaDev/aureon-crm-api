package com.example.boilerplate.crm.lead;

import com.example.boilerplate.crm.common.CrmRecord;
import com.example.boilerplate.crm.common.Money;
import com.example.boilerplate.crm.common.Priority;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "leads")
public class Lead extends CrmRecord {
  @Column(nullable = false, length = 100)
  private String firstName;

  @Column(length = 100)
  private String lastName;

  @Column(length = 200)
  private String companyName;

  @Column(length = 254)
  private String email;

  @Column(length = 50)
  private String phone;

  @Column(length = 100)
  private String jobTitle;

  private Long sourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private LeadStatus status = LeadStatus.NEW;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Priority priority;

  private LocalDate followUpDate;

  @Column(precision = 15, scale = 2)
  private BigDecimal estimatedValue;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  @Column(length = 500)
  private String unqualifiedReason;

  @Column(length = 5000)
  private String notes;

  private Instant convertedAt;

  // Filled by the conversion (it also sets the deal, whose column has no mapping yet).
  private Long convertedCompanyId;

  private Long convertedContactId;

  /** Empty until {@link #update}: a new lead is filled the way an edit fills it. */
  public Lead() {}

  /**
   * Every editable field but the owner ({@link #changeOwner}) and the status ({@link
   * #changeStatus}); null clears an optional one. The link is already checked; the value comes with
   * its currency or both are null.
   */
  public void update(
      String firstName,
      String lastName,
      String companyName,
      String email,
      String phone,
      String jobTitle,
      Long sourceId,
      Priority priority,
      LocalDate followUpDate,
      BigDecimal estimatedValue,
      String currency,
      String notes) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.companyName = companyName;
    this.email = email;
    this.phone = phone;
    this.jobTitle = jobTitle;
    this.sourceId = sourceId;
    this.priority = priority;
    this.followUpDate = followUpDate;
    this.estimatedValue = Money.scale2(estimatedValue);
    this.currency = currency;
    this.notes = notes;
  }

  /**
   * The caller has checked that the move is allowed ({@link LeadStatus#canMoveTo}) and that
   * UNQUALIFIED has a reason; any other status forgets it.
   */
  public void changeStatus(LeadStatus status, String unqualifiedReason) {
    this.status = status;
    this.unqualifiedReason = status == LeadStatus.UNQUALIFIED ? unqualifiedReason : null;
  }

  public boolean isConverted() {
    return status == LeadStatus.CONVERTED;
  }

  /** The first and the last name, or the first name alone. */
  public String getFullName() {
    return lastName == null ? firstName : firstName + " " + lastName;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getCompanyName() {
    return companyName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public Long getSourceId() {
    return sourceId;
  }

  public LeadStatus getStatus() {
    return status;
  }

  public Priority getPriority() {
    return priority;
  }

  public LocalDate getFollowUpDate() {
    return followUpDate;
  }

  public BigDecimal getEstimatedValue() {
    return estimatedValue;
  }

  public String getCurrency() {
    return currency;
  }

  public String getUnqualifiedReason() {
    return unqualifiedReason;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getConvertedAt() {
    return convertedAt;
  }

  public Long getConvertedCompanyId() {
    return convertedCompanyId;
  }

  public Long getConvertedContactId() {
    return convertedContactId;
  }
}
