package com.example.boilerplate.crm.lead;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.Currencies;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.common.RecordLinks;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.lead.dto.*;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current workspace's leads. */
@Service
@Transactional
public class LeadService {
  private final LeadRepository leads;
  private final LeadSourceRepository sources;
  private final CompanyRepository companies;
  private final ContactRepository contacts;
  private final DealRepository deals;
  private final WorkspaceRepository workspaces;
  private final RecordLinks links;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public LeadService(
      LeadRepository leads,
      LeadSourceRepository sources,
      CompanyRepository companies,
      ContactRepository contacts,
      DealRepository deals,
      WorkspaceRepository workspaces,
      RecordLinks links,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.leads = leads;
    this.sources = sources;
    this.companies = companies;
    this.contacts = contacts;
    this.deals = deals;
    this.workspaces = workspaces;
    this.links = links;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  @Transactional(readOnly = true)
  public Page<LeadResponse> list(
      LeadFilter filter, int page, int size, LeadSort sort, Direction direction) {
    Specification<Lead> matching = LeadSpecifications.matching(filter);
    Specification<Lead> ordering = LeadSpecifications.ordering(sort, direction);
    Page<Lead> found =
        ordering == null
            ? leads.findAll(matching, ListParams.pageable(page, size, direction, sort))
            : leads.findAll(matching.and(ordering), ListParams.unsorted(page, size));
    Related related = related(found.getContent());
    return found.map(l -> response(l, related));
  }

  @Transactional(readOnly = true)
  public LeadResponse get(long id) {
    return response(lead(id));
  }

  public LeadResponse create(CreateLeadRequest r) {
    long owner = access.ownerForCreate("ownerId", r.ownerId());
    links.source(r.sourceId());
    String currency = currency(r);
    Lead lead = new Lead();
    edit(lead, r, currency);
    lead.changeOwner(owner);
    leads.save(lead);
    audit.record(
        AuditAction.CREATE, AuditEntityType.LEAD, lead.getId(), lead.getFullName(), Map.of());
    return response(lead);
  }

  public LeadResponse update(long id, UpdateLeadRequest r) {
    Lead lead = lead(id);
    access.requireEditable(lead.getOwnerId());
    // Before the version check: a stale client should learn that nothing can be edited any more.
    if (lead.isConverted())
      throw new ConflictException(ErrorCode.LEAD_CONVERTED, "Converted leads can't be edited");
    Versions.check(lead, r.version());
    Long owner = access.ownerForUpdate("ownerId", r.ownerId(), lead.getOwnerId());
    links.source(r.sourceId());
    String currency = currency(r);
    LeadResponse before = response(lead);
    edit(lead, r, currency);
    lead.changeOwner(owner);
    // Flush so the response carries the new version.
    LeadResponse after = response(leads.saveAndFlush(lead));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.LEAD, id, lead.getFullName(), changes);
    }
    return after;
  }

  /**
   * Moves the lead to another status of the open four ({@link LeadStatus#canMoveTo}). UNQUALIFIED
   * needs a reason, which any other status clears. The same status again changes nothing, except
   * UNQUALIFIED with a different reason, which saves the reason.
   */
  public LeadResponse changeStatus(long id, LeadStatusRequest r) {
    Lead lead = lead(id);
    access.requireEditable(lead.getOwnerId());
    LeadStatus current = lead.getStatus();
    LeadStatus target = r.status();
    // Before the version check, like a PUT: these don't depend on what the client last read.
    if (target == LeadStatus.CONVERTED) {
      throw new FieldValidationException("status", "can't be set here: convert the lead instead");
    }
    if (current != target && !current.canMoveTo(target)) {
      throw new ConflictException(ErrorCode.LEAD_CONVERTED, "Converted leads can't change status");
    }
    String reason = Texts.optional(r.reason());
    if (target == LeadStatus.UNQUALIFIED && reason == null && current != target) {
      throw new FieldValidationException("reason", "is required to mark a lead unqualified");
    }
    Versions.check(lead, r.version());
    boolean newReason =
        target == LeadStatus.UNQUALIFIED
            && reason != null
            && !reason.equals(lead.getUnqualifiedReason());
    if (current == target && !newReason) return response(lead);
    LeadResponse before = response(lead);
    lead.changeStatus(target, reason);
    LeadResponse after = response(leads.saveAndFlush(lead));
    audit.record(
        AuditAction.STATUS_CHANGE,
        AuditEntityType.LEAD,
        id,
        lead.getFullName(),
        auditChanges.recordDiff(before, after));
    return after;
  }

  public LeadResponse archive(long id) {
    return setArchived(id, true);
  }

  public LeadResponse restore(long id) {
    return setArchived(id, false);
  }

  /** Idempotent: a lead already in that state comes back unchanged, with no audit event. */
  private LeadResponse setArchived(long id, boolean archived) {
    Lead lead = lead(id);
    access.requireEditable(lead.getOwnerId());
    if (lead.isArchived() != archived) {
      if (archived) lead.archive();
      else lead.restore();
      leads.saveAndFlush(lead);
      audit.record(
          archived ? AuditAction.ARCHIVE : AuditAction.RESTORE,
          AuditEntityType.LEAD,
          id,
          lead.getFullName(),
          Map.of());
    }
    return response(lead);
  }

  private Lead lead(long id) {
    return leads.findById(id).orElseThrow(() -> new NotFoundException("Lead not found"));
  }

  /**
   * The currency of the estimated value: both or neither. A value takes the request's currency
   * (upper-cased, checked) or the workspace's; a currency without a value is a 400 on the value.
   */
  private String currency(LeadInput r) {
    String requested = Texts.optional(r.currency());
    if (r.estimatedValue() == null) {
      if (requested != null) {
        throw new FieldValidationException("estimatedValue", "is required with a currency");
      }
      return null;
    }
    String workspaceDefault =
        workspaces.findById(WorkspaceContext.workspaceId()).orElseThrow().getDefaultCurrency();
    return Currencies.resolve(requested, workspaceDefault);
  }

  /**
   * Create and update apply the request alike; the owner is decided and the links are checked by
   * the caller.
   */
  private static void edit(Lead lead, LeadInput r, String currency) {
    String email = Texts.optional(r.email());
    lead.update(
        Texts.required("firstName", r.firstName()),
        Texts.optional(r.lastName()),
        Texts.optional(r.companyName()),
        email == null ? null : email.toLowerCase(Locale.ROOT),
        Texts.optional(r.phone()),
        Texts.optional(r.jobTitle()),
        r.sourceId(),
        r.priority() == null ? Priority.MEDIUM : r.priority(),
        r.followUpDate(),
        r.estimatedValue(),
        currency,
        Texts.optional(r.notes()));
  }

  /** What a page shows besides the leads' own columns: one query each for a whole page. */
  private record Related(
      Map<Long, MemberRef> owners,
      Map<Long, Ref> sources,
      Map<Long, Ref> convertedCompanies,
      Map<Long, PersonRef> convertedContacts,
      Map<Long, Ref> convertedDeals) {
    MemberRef owner(Lead l) {
      return l.getOwnerId() == null ? null : owners.get(l.getOwnerId());
    }

    Ref source(Lead l) {
      return l.getSourceId() == null ? null : sources.get(l.getSourceId());
    }

    Ref convertedCompany(Lead l) {
      return l.getConvertedCompanyId() == null
          ? null
          : convertedCompanies.get(l.getConvertedCompanyId());
    }

    PersonRef convertedContact(Lead l) {
      return l.getConvertedContactId() == null
          ? null
          : convertedContacts.get(l.getConvertedContactId());
    }

    Ref convertedDeal(Lead l) {
      return l.getConvertedDealId() == null ? null : convertedDeals.get(l.getConvertedDealId());
    }
  }

  private Related related(List<Lead> page) {
    if (page.isEmpty()) return new Related(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    return new Related(
        directory.refs(page.stream().map(Lead::getOwnerId).toList()),
        sources.findAllById(ids(page, Lead::getSourceId)).stream()
            .collect(Collectors.toMap(s -> s.getId(), s -> new Ref(s.getId(), s.getName()))),
        companies.findAllById(ids(page, Lead::getConvertedCompanyId)).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> new Ref(c.getId(), c.getName()))),
        contacts.findAllById(ids(page, Lead::getConvertedContactId)).stream()
            .collect(
                Collectors.toMap(c -> c.getId(), c -> new PersonRef(c.getId(), c.getFullName()))),
        deals.findAllById(ids(page, Lead::getConvertedDealId)).stream()
            .collect(Collectors.toMap(d -> d.getId(), d -> new Ref(d.getId(), d.getName()))));
  }

  private static List<Long> ids(List<Lead> page, Function<Lead, Long> link) {
    return page.stream().map(link).filter(Objects::nonNull).distinct().toList();
  }

  /** Also the conversion's before and after, for its audit changes. */
  LeadResponse response(Lead l) {
    return response(l, related(List.of(l)));
  }

  private static LeadResponse response(Lead l, Related r) {
    return new LeadResponse(
        l.getId(),
        l.getFirstName(),
        l.getLastName(),
        l.getCompanyName(),
        l.getEmail(),
        l.getPhone(),
        l.getJobTitle(),
        r.source(l),
        r.owner(l),
        l.getPriority(),
        l.getFollowUpDate(),
        l.getEstimatedValue(),
        l.getCurrency(),
        l.getNotes(),
        l.getStatus(),
        l.getUnqualifiedReason(),
        l.getConvertedAt(),
        r.convertedCompany(l),
        r.convertedContact(l),
        r.convertedDeal(l),
        l.isArchived(),
        l.getArchivedAt(),
        l.getCreatedAt(),
        l.getUpdatedAt(),
        l.getVersion());
  }
}
