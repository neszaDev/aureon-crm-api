package com.example.boilerplate.crm.settings;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.lead.LeadRepository;
import com.example.boilerplate.crm.settings.dto.*;
import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceAccess;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current workspace's lead sources. */
@Service
@Transactional
public class LeadSourceService {
  private final LeadSourceRepository sources;
  private final LeadRepository leads;
  private final ContactRepository contacts;
  private final DealRepository deals;
  private final WorkspaceAccess access;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public LeadSourceService(
      LeadSourceRepository sources,
      LeadRepository leads,
      ContactRepository contacts,
      DealRepository deals,
      WorkspaceAccess access,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.sources = sources;
    this.leads = leads;
    this.contacts = contacts;
    this.deals = deals;
    this.access = access;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  @Transactional(readOnly = true)
  public List<LeadSourceResponse> list(boolean includeArchived) {
    return sources.list(includeArchived).stream().map(LeadSourceService::response).toList();
  }

  public LeadSourceResponse create(CreateLeadSourceRequest r) {
    access.require(Permission.SETTINGS_MANAGE);
    LeadSource source = sources.save(new LeadSource(uniqueName(r.name(), 0)));
    audit.record(
        AuditAction.CREATE,
        AuditEntityType.LEAD_SOURCE,
        source.getId(),
        source.getName(),
        Map.of());
    return response(source);
  }

  public LeadSourceResponse update(long id, UpdateLeadSourceRequest r) {
    access.require(Permission.SETTINGS_MANAGE);
    LeadSource source = source(id);
    Versions.check(source, r.version());
    String name = uniqueName(r.name(), id);
    LeadSourceResponse before = response(source);
    source.update(name, r.archived());
    // Flush so the response carries the new version.
    LeadSourceResponse after = response(sources.saveAndFlush(source));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.LEAD_SOURCE, id, name, changes);
    }
    return after;
  }

  public void delete(long id) {
    access.require(Permission.SETTINGS_MANAGE);
    LeadSource source = source(id);
    // Archived records too: their foreign keys hold the source.
    if (leads.existsBySourceId(id) || contacts.existsBySourceId(id) || deals.existsBySourceId(id)) {
      throw new ConflictException(
          ErrorCode.SOURCE_IN_USE, "Lead source is in use; archive it instead");
    }
    sources.delete(source);
    audit.record(AuditAction.DELETE, AuditEntityType.LEAD_SOURCE, id, source.getName(), Map.of());
  }

  private LeadSource source(long id) {
    return sources.findById(id).orElseThrow(() -> new NotFoundException("Lead source not found"));
  }

  /** The name without blanks around it, unless another source has it (any case); 0: a new one. */
  private String uniqueName(String name, long exceptId) {
    String stripped = Texts.required("name", name);
    if (sources.existsByNameIgnoreCaseAndIdNot(stripped, exceptId)) {
      throw new ConflictException(
          ErrorCode.DUPLICATE_NAME, "A lead source with this name already exists");
    }
    return stripped;
  }

  private static LeadSourceResponse response(LeadSource s) {
    return new LeadSourceResponse(s.getId(), s.getName(), s.isArchived(), s.getVersion());
  }
}
