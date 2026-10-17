package com.example.boilerplate.crm.contact;

import com.example.boilerplate.crm.common.CrmRecord;
import com.example.boilerplate.crm.company.Company;
import jakarta.persistence.*;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contacts")
public class Contact extends CrmRecord {
  @Column(nullable = false, length = 100)
  private String firstName;

  @Column(length = 100)
  private String lastName;

  @Column(length = 254)
  private String email;

  @Column(length = 50)
  private String phone;

  @Column(length = 100)
  private String jobTitle;

  @Column(name = "company_id")
  private Long companyId;

  /** Read-only, for the company sort's join; the service writes {@link #companyId}. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  private Long sourceId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> tags = List.of();

  @Column(length = 5000)
  private String notes;

  /** Empty until {@link #update}: a new contact is filled the way an edit fills it. */
  public Contact() {}

  /**
   * Every editable field but the owner ({@link #changeOwner}); null clears an optional one. The
   * links and tags are already checked and normalized.
   */
  public void update(
      String firstName,
      String lastName,
      String email,
      String phone,
      String jobTitle,
      Long companyId,
      Long sourceId,
      List<String> tags,
      String notes) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.phone = phone;
    this.jobTitle = jobTitle;
    this.companyId = companyId;
    this.sourceId = sourceId;
    this.tags = tags;
    this.notes = notes;
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

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public Long getSourceId() {
    return sourceId;
  }

  public List<String> getTags() {
    return tags;
  }

  public String getNotes() {
    return notes;
  }
}
