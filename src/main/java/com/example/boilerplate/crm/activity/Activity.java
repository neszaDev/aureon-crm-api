package com.example.boilerplate.crm.activity;

import com.example.boilerplate.common.entity.WorkspaceOwnedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A call, email, meeting or note logged by a member about a company, contact, lead or deal. */
@Entity
@Table(name = "activities")
public class Activity extends WorkspaceOwnedEntity {
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ActivityType type;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(length = 5000)
  private String body;

  @Column(nullable = false)
  private Instant occurredAt;

  /**
   * A member's user id, null once they left the workspace: the database clears it, so it is never
   * written after the insert.
   */
  @Column(updatable = false)
  private Long authorId;

  private Long companyId;

  private Long contactId;

  private Long leadId;

  private Long dealId;

  protected Activity() {}

  /** A new activity written by the member {@code authorId}; {@link #update} fills it. */
  public Activity(long authorId) {
    this.authorId = authorId;
  }

  /**
   * Every editable field; null clears an optional one. The links are already checked and the
   * company is already derived. The time is kept in microseconds, as stored, so a response equals a
   * later read and an unchanged time is no change.
   */
  public void update(
      ActivityType type,
      String subject,
      String body,
      Instant occurredAt,
      Long companyId,
      Long contactId,
      Long leadId,
      Long dealId) {
    this.type = type;
    this.subject = subject;
    this.body = body;
    this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    this.companyId = companyId;
    this.contactId = contactId;
    this.leadId = leadId;
    this.dealId = dealId;
  }

  public ActivityType getType() {
    return type;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Long getAuthorId() {
    return authorId;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public Long getContactId() {
    return contactId;
  }

  public Long getLeadId() {
    return leadId;
  }

  public Long getDealId() {
    return dealId;
  }
}
