package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.Contact;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.Deal;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.lead.Lead;
import com.example.boilerplate.crm.lead.LeadRepository;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

/**
 * The links of a request body ({@code companyId}, {@code sourceId}, ...), checked before a record
 * is saved: each names a row of the current workspace, or the request is a 400 on that field. The
 * tenant-filtered {@code findById} doesn't see other workspaces, so their ids are "not found" too;
 * the composite foreign keys are the backstop. Null (no link) passes. Activities and tasks also
 * take the links that follow from the ones they name ({@link #contactFor}, {@link #companyFor}).
 */
@Component
public class RecordLinks {
  private final CompanyRepository companies;
  private final LeadSourceRepository sources;
  private final ContactRepository contacts;
  private final LeadRepository leads;
  private final DealRepository deals;

  public RecordLinks(
      CompanyRepository companies,
      LeadSourceRepository sources,
      ContactRepository contacts,
      LeadRepository leads,
      DealRepository deals) {
    this.companies = companies;
    this.sources = sources;
    this.contacts = contacts;
    this.leads = leads;
    this.deals = deals;
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

  public void lead(Long id) {
    check("leadId", id, leads);
  }

  public void deal(Long id) {
    check("dealId", id, deals);
  }

  /**
   * The contact the request names; without one, the contact a converted lead became, so what is
   * logged on the lead afterwards shows on the contact. Call after the checks: the rows load from
   * the session.
   */
  public Long contactFor(Long contactId, Long leadId) {
    if (contactId != null || leadId == null) return contactId;
    return leads.findById(leadId).map(Lead::getConvertedContactId).orElse(null);
  }

  /**
   * The company the request names; without one, that of the contact, else that of the deal, which
   * puts the record on the company's timeline and task list. Call after the checks.
   */
  public Long companyFor(Long companyId, Long contactId, Long dealId) {
    if (companyId != null) return companyId;
    Long ofContact =
        contactId == null
            ? null
            : contacts.findById(contactId).map(Contact::getCompanyId).orElse(null);
    if (ofContact != null) return ofContact;
    return dealId == null ? null : deals.findById(dealId).map(Deal::getCompanyId).orElse(null);
  }

  private static void check(String field, Long id, CrudRepository<?, Long> rows) {
    if (id != null && rows.findById(id).isEmpty()) {
      throw new FieldValidationException(field, "is not found in this workspace");
    }
  }
}
