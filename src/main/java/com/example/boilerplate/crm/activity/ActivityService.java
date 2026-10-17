package com.example.boilerplate.crm.activity;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.activity.dto.*;
import com.example.boilerplate.crm.common.LinkRefs;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.RecordLinks;
import com.example.boilerplate.crm.contact.Contact;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.Deal;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current workspace's activities. The author is the caller who logged it, for good; the author
 * or anyone with {@code RECORDS_EDIT_ANY} may edit or delete it. Activities are the one record type
 * that is deleted rather than archived.
 */
@Service
@Transactional
public class ActivityService {
  private final ActivityRepository activities;
  private final ContactRepository contacts;
  private final DealRepository deals;
  private final LinkRefs linkRefs;
  private final RecordLinks links;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public ActivityService(
      ActivityRepository activities,
      ContactRepository contacts,
      DealRepository deals,
      LinkRefs linkRefs,
      RecordLinks links,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.activities = activities;
    this.contacts = contacts;
    this.deals = deals;
    this.linkRefs = linkRefs;
    this.links = links;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  /** Newest {@code occurredAt} first, the id breaking ties the same way. */
  @Transactional(readOnly = true)
  public Page<ActivityResponse> list(ActivityFilter filter, int page, int size) {
    Page<Activity> found =
        activities.findAll(
            ActivitySpecifications.matching(filter),
            ListParams.pageable(page, size, Direction.DESC, "occurredAt"));
    Related related = related(found.getContent());
    return found.map(a -> response(a, related));
  }

  @Transactional(readOnly = true)
  public ActivityResponse get(long id) {
    return response(activity(id));
  }

  public ActivityResponse create(CreateActivityRequest r) {
    checkLinks(r);
    Activity activity = new Activity(WorkspaceContext.require().userId());
    edit(activity, r, Instant.now());
    activities.save(activity);
    audit.record(
        AuditAction.CREATE,
        AuditEntityType.ACTIVITY,
        activity.getId(),
        activity.getSubject(),
        Map.of());
    return response(activity);
  }

  public ActivityResponse update(long id, UpdateActivityRequest r) {
    Activity activity = activity(id);
    requireEditable(activity);
    Versions.check(activity, r.version());
    checkLinks(r);
    ActivityResponse before = response(activity);
    // An edit that leaves the time out keeps it: "now" is the default of a new activity.
    edit(activity, r, activity.getOccurredAt());
    // Flush so the response carries the new version.
    ActivityResponse after = response(activities.saveAndFlush(activity));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(
          AuditAction.UPDATE, AuditEntityType.ACTIVITY, id, activity.getSubject(), changes);
    }
    return after;
  }

  /** The event keeps what the activity was, every field as the "from" of a change to nothing. */
  public void delete(long id) {
    Activity activity = activity(id);
    requireEditable(activity);
    ActivityResponse last = response(activity);
    activities.delete(activity);
    audit.record(
        AuditAction.DELETE,
        AuditEntityType.ACTIVITY,
        id,
        activity.getSubject(),
        auditChanges.recordDiff(last, Map.of()));
  }

  private Activity activity(long id) {
    return activities.findById(id).orElseThrow(() -> new NotFoundException("Activity not found"));
  }

  /** Its author, or anyone with {@code RECORDS_EDIT_ANY}: nobody, once the author has left. */
  private void requireEditable(Activity activity) {
    access.requireEditableBy(activity.getAuthorId());
  }

  /** At least one link, and each of them a record of this workspace. */
  private void checkLinks(ActivityInput r) {
    if (r.companyId() == null
        && r.contactId() == null
        && r.leadId() == null
        && r.dealId() == null) {
      throw new FieldValidationException(
          "links", "Link the activity to a company, contact, lead or deal");
    }
    links.company(r.companyId());
    links.contact(r.contactId());
    links.lead(r.leadId());
    links.deal(r.dealId());
  }

  /**
   * Create and update apply the request alike; the links are checked by the caller. {@code
   * defaultTime} is the time of an activity whose request names none.
   */
  private void edit(Activity activity, ActivityInput r, Instant defaultTime) {
    activity.update(
        r.type(),
        Texts.required("subject", r.subject()),
        Texts.optional(r.body()),
        r.occurredAt() == null ? defaultTime : r.occurredAt(),
        companyFor(r),
        r.contactId(),
        r.leadId(),
        r.dealId());
  }

  /**
   * The company the request names; without one, that of its contact, else that of its deal, which
   * puts the activity on the company's timeline. The links are checked, so they load from the
   * session, not the database.
   */
  private Long companyFor(ActivityInput r) {
    if (r.companyId() != null) return r.companyId();
    Long ofContact =
        r.contactId() == null
            ? null
            : contacts.findById(r.contactId()).map(Contact::getCompanyId).orElse(null);
    if (ofContact != null) return ofContact;
    return r.dealId() == null
        ? null
        : deals.findById(r.dealId()).map(Deal::getCompanyId).orElse(null);
  }

  /** What a page shows besides the activities' own columns: one query each for a whole page. */
  private record Related(Map<Long, MemberRef> people, LinkRefs.Refs links) {
    MemberRef author(Activity a) {
      return a.getAuthorId() == null ? null : people.get(a.getAuthorId());
    }
  }

  private Related related(List<Activity> page) {
    return new Related(
        directory.refs(column(page, Activity::getAuthorId)),
        linkRefs.of(
            column(page, Activity::getCompanyId),
            column(page, Activity::getContactId),
            column(page, Activity::getLeadId),
            column(page, Activity::getDealId)));
  }

  /** One column of the page, null (no link) and repeats included: the callers filter them. */
  private static List<Long> column(List<Activity> page, Function<Activity, Long> field) {
    return page.stream().map(field).toList();
  }

  private ActivityResponse response(Activity a) {
    return response(a, related(List.of(a)));
  }

  private static ActivityResponse response(Activity a, Related r) {
    return new ActivityResponse(
        a.getId(),
        a.getType(),
        a.getSubject(),
        a.getBody(),
        a.getOccurredAt(),
        r.author(a),
        r.links().company(a.getCompanyId()),
        r.links().contact(a.getContactId()),
        r.links().lead(a.getLeadId()),
        r.links().deal(a.getDealId()),
        a.getCreatedAt(),
        a.getUpdatedAt(),
        a.getVersion());
  }
}
