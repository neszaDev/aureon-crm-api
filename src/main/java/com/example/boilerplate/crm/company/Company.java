package com.example.boilerplate.crm.company;

import com.example.boilerplate.crm.common.CrmRecord;
import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company extends CrmRecord {
  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 100)
  private String industry;

  @Column(length = 255)
  private String website;

  @Column(length = 50)
  private String phone;

  @Column(length = 500)
  private String address;

  private Integer employeeCount;

  @Column(length = 5000)
  private String notes;

  /** Empty until {@link #update}: a new company is filled the way an edit fills it. */
  public Company() {}

  /** Every editable field but the owner ({@link #changeOwner}); null clears an optional one. */
  public void update(
      String name,
      String industry,
      String website,
      String phone,
      String address,
      Integer employeeCount,
      String notes) {
    this.name = name;
    this.industry = industry;
    this.website = website;
    this.phone = phone;
    this.address = address;
    this.employeeCount = employeeCount;
    this.notes = notes;
  }

  public String getName() {
    return name;
  }

  public String getIndustry() {
    return industry;
  }

  public String getWebsite() {
    return website;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddress() {
    return address;
  }

  public Integer getEmployeeCount() {
    return employeeCount;
  }

  public String getNotes() {
    return notes;
  }
}
