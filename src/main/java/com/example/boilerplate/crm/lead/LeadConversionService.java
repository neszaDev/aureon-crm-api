package com.example.boilerplate.crm.lead;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.crm.activity.ActivityRepository;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.company.Company;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.company.CompanyService;
import com.example.boilerplate.crm.company.dto.CreateCompanyRequest;
import com.example.boilerplate.crm.contact.Contact;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.contact.ContactService;
import com.example.boilerplate.crm.contact.dto.CreateContactRequest;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.deal.DealService;
import com.example.boilerplate.crm.deal.dto.CreateDealRequest;
import com.example.boilerplate.crm.lead.dto.LeadConversionInput;
import com.example.boilerplate.crm.lead.dto.LeadConversionInput.DealDraft;
import com.example.boilerplate.crm.lead.dto.LeadConversionResponse;
import com.example.boilerplate.crm.lead.dto.LeadResponse;
import com.example.boilerplate.crm.task.TaskRepository;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a qualified lead into a contact, and optionally a company and a deal, in one transaction.
 * The lead's row lock makes concurrent conversions of one lead run one after the other, so the
 * second finds it converted; {@code deals (workspace_id, lead_id) UNIQUE} and the lead's version
 * are the backstops (409).
 */
@Service
@Transactional
public class LeadConversionService {
  private final LeadRepository leads;
  private final LeadService leadService;
  private final CompanyRepository companies;
  private final ContactRepository contacts;
  private final DealRepository deals;
  private final ActivityRepository activities;
  private final TaskRepository tasks;
  private final CompanyService companyService;
  private final ContactService contactService;
  private final DealService dealService;
  private final WorkspaceAccess access;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public LeadConversionService(
      LeadRepository leads,
      LeadService leadService,
      CompanyRepository companies,
      ContactRepository contacts,
      DealRepository deals,
      ActivityRepository activities,
      TaskRepository tasks,
      CompanyService companyService,
      ContactService contactService,
      DealService dealService,
      WorkspaceAccess access,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.leads = leads;
    this.leadService = leadService;
    this.companies = companies;
    this.contacts = contacts;
    this.deals = deals;
    this.activities = activities;
    this.tasks = tasks;
    this.companyService = companyService;
    this.contactService = contactService;
    this.dealService = dealService;
    this.access = access;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  /**
   * Links or creates the company (when asked, from the lead's company name), links or creates the
   * contact from the lead, creates the deal when one is given, and marks the lead converted. The
   * new records belong to the lead's owner, or to the caller when the lead has none (only someone
   * who may edit any record gets that far). A lead converted before returns that first result and
   * writes nothing; any failure leaves nothing behind. The lead's activities and tasks are linked
   * to the contact, company and deal ({@link #linkHistory}).
   */
  public LeadConversionResponse convert(long id, LeadConversionInput r) {
    Lead lead = leads.findForUpdate(id).orElseThrow(() -> new NotFoundException("Lead not found"));
    // First: a retry is answered only to someone who could have converted the lead.
    access.requireEditable(lead.getOwnerId());
    if (lead.isConverted()) return result(lead, true);
    if (lead.getStatus() != LeadStatus.QUALIFIED) {
      throw new ConflictException(
          ErrorCode.LEAD_NOT_QUALIFIED, "Only qualified leads can be converted");
    }
    long owner =
        lead.getOwnerId() != null ? lead.getOwnerId() : WorkspaceContext.require().userId();

    Contact existing = existingContact(r.contactId());
    Long companyId = company(lead, r, owner);
    long contactId = r.contactId() != null ? r.contactId() : newContact(lead, companyId, owner);
    Long dealId = r.deal() == null ? null : newDeal(lead, r.deal(), companyId, contactId, owner);

    LeadResponse before = leadService.response(lead);
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    lead.markConverted(companyId, contactId, dealId, now);
    LeadResponse after = leadService.response(leads.saveAndFlush(lead));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    // As an activity logged on the contact: without a company in the request, the contact's.
    Long historyCompany =
        companyId == null && existing != null ? existing.getCompanyId() : companyId;
    changes.putAll(linkHistory(id, contactId, historyCompany, dealId, now));
    audit.record(AuditAction.CONVERT, AuditEntityType.LEAD, id, lead.getFullName(), changes);
    return result(lead, false);
  }

  /**
   * The lead's activities and tasks join the contact (and company and deal), so the contact's
   * timeline shows them; no event of their own, the CONVERT one counts them: {@code
   * {linkedActivities: {from: null, to: 3}}}, a key only when there are some.
   */
  private Map<String, Map<String, Object>> linkHistory(
      long leadId, long contactId, Long companyId, Long dealId, Instant now) {
    long workspaceId = WorkspaceContext.workspaceId();
    Map<String, Object> linked = new LinkedHashMap<>();
    int a = activities.linkConvertedLead(workspaceId, leadId, contactId, companyId, dealId, now);
    if (a > 0) linked.put("linkedActivities", a);
    int t = tasks.linkConvertedLead(workspaceId, leadId, contactId, companyId, dealId, now);
    if (t > 0) linked.put("linkedTasks", t);
    return AuditChanges.diff(Map.of(), linked);
  }

  /** The contact to link, if any: of this workspace and active, like the company. */
  private Contact existingContact(Long id) {
    if (id == null) return null;
    Contact contact =
        contacts
            .findById(id)
            .orElseThrow(
                () -> new FieldValidationException("contactId", "is not found in this workspace"));
    if (contact.isArchived()) throw new FieldValidationException("contactId", "is archived");
    return contact;
  }

  /** The given company (active, of this workspace), a new one from the lead, or none. */
  private Long company(Lead lead, LeadConversionInput r, long owner) {
    if (r.companyId() != null) {
      Company company =
          companies
              .findById(r.companyId())
              .orElseThrow(
                  () ->
                      new FieldValidationException("companyId", "is not found in this workspace"));
      if (company.isArchived()) throw new FieldValidationException("companyId", "is archived");
      return company.getId();
    }
    if (!r.createCompany() || lead.getCompanyName() == null) return null;
    return companyService
        .create(
            new CreateCompanyRequest(
                lead.getCompanyName(), null, null, null, null, null, null, null),
            owner)
        .getId();
  }

  private long newContact(Lead lead, Long companyId, long owner) {
    return contactService
        .create(
            new CreateContactRequest(
                lead.getFirstName(),
                lead.getLastName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getJobTitle(),
                companyId,
                null,
                lead.getSourceId(),
                null,
                null),
            owner)
        .getId();
  }

  /** The deal rules' errors name the request's field: {@code deal.stageId}, ... */
  private long newDeal(Lead lead, DealDraft d, Long companyId, Long contactId, long owner) {
    CreateDealRequest request =
        new CreateDealRequest(
            d.name(),
            d.amount(),
            d.currency(),
            d.stageId(),
            null,
            d.expectedCloseDate(),
            null,
            companyId,
            contactId,
            lead.getSourceId(),
            null);
    try {
      return dealService.create(request, owner, lead.getId()).getId();
    } catch (FieldValidationException e) {
      throw new FieldValidationException("deal." + e.field(), e.getMessage(), e.code());
    }
  }

  /** From the lead's stored ids: a retry shows what the first conversion linked or created. */
  private LeadConversionResponse result(Lead lead, boolean alreadyConverted) {
    Ref company =
        lead.getConvertedCompanyId() == null
            ? null
            : companies
                .findById(lead.getConvertedCompanyId())
                .map(c -> new Ref(c.getId(), c.getName()))
                .orElse(null);
    PersonRef contact =
        lead.getConvertedContactId() == null
            ? null
            : contacts
                .findById(lead.getConvertedContactId())
                .map(c -> new PersonRef(c.getId(), c.getFullName()))
                .orElse(null);
    Ref deal =
        lead.getConvertedDealId() == null
            ? null
            : deals
                .findById(lead.getConvertedDealId())
                .map(d -> new Ref(d.getId(), d.getName()))
                .orElse(null);
    return new LeadConversionResponse(lead.getId(), company, contact, deal, alreadyConverted);
  }
}
