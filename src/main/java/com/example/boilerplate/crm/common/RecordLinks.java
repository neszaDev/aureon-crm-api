package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

/**
 * The links of a request body ({@code companyId}, {@code sourceId}, ...), checked before a record
 * is saved: each names a row of the current workspace, or the request is a 400 on that field. The
 * tenant-filtered {@code findById} doesn't see other workspaces, so their ids are "not found" too;
 * the composite foreign keys are the backstop. Null (no link) passes.
 */
@Component
public class RecordLinks {
  private final CompanyRepository companies;
  private final LeadSourceRepository sources;
  private final ContactRepository contacts;

  public RecordLinks(
      CompanyRepository companies, LeadSourceRepository sources, ContactRepository contacts) {
    this.companies = companies;
    this.sources = sources;
    this.contacts = contacts;
  }

  public void company(Long id) {
    check("companyId", id, companies);
  }

  public void source(Long id) {
    check("sourceId", id, sources);
  }

  public void contact(Long id) {
    check("contactId", id, contacts);
  }

  private static void check(String field, Long id, CrudRepository<?, Long> rows) {
    if (id != null && rows.findById(id).isEmpty()) {
      throw new FieldValidationException(field, "is not found in this workspace");
    }
  }
}
