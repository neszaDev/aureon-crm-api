package com.example.boilerplate.crm.contact;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.RecordLinks;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.dto.*;
import com.example.boilerplate.crm.export.CsvFile;
import com.example.boilerplate.crm.export.Exporter;
import com.example.boilerplate.crm.export.RecordRefs;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current workspace's contacts. */
@Service
@Transactional
public class ContactService {
  private static final int MAX_DUPLICATES = 5;
  private static final int MIN_PHONE_DIGITS = 6;
  private static final int MAX_TAGS = 20;
  private static final int MAX_TAG_LENGTH = 40;
  private static final List<String> EXPORT_HEADER =
      List.of(
          "Reference",
          "First name",
          "Last name",
          "Email",
          "Phone",
          "Job title",
          "Company",
          "Owner",
          "Source",
          "Tags",
          "Created",
          "Updated",
          "Archived");

  private final ContactRepository contacts;
  private final CompanyRepository companies;
  private final LeadSourceRepository sources;
  private final RecordLinks links;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;
  private final Exporter exporter;

  public ContactService(
      ContactRepository contacts,
      CompanyRepository companies,
      LeadSourceRepository sources,
      RecordLinks links,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges,
      Exporter exporter) {
    this.contacts = contacts;
    this.companies = companies;
    this.sources = sources;
    this.links = links;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
    this.exporter = exporter;
  }

  @Transactional(readOnly = true)
  public Page<ContactSummary> list(
      ContactFilter filter, int page, int size, ContactSort sort, Direction direction) {
    Page<Contact> found =
        contacts.findAll(
            ContactSpecifications.matching(filter),
            ListParams.pageable(page, size, direction, sort));
    Related related = related(found.getContent());
    return found.map(c -> summary(c, related));
  }

  /** The list's contacts (its filter, its default order) as CSV (api.md, Export). */
  public CsvFile export(ContactFilter filter) {
    return exporter.export(
        "contacts",
        filter,
        EXPORT_HEADER,
        contacts,
        ContactSpecifications.matching(filter),
        ListParams.sort(ContactSort.NAME, Direction.ASC),
        found -> {
          Related r = related(found);
          Map<Long, String> sourceNames =
              sources.findAllById(ids(found, Contact::getSourceId)).stream()
                  .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
          return found.stream()
              .map(
                  c ->
                      Exporter.row(
                          RecordRefs.format('P', c.getId()),
                          c.getFirstName(),
                          c.getLastName(),
                          c.getEmail(),
                          c.getPhone(),
                          c.getJobTitle(),
                          r.company(c),
                          r.owner(c),
                          sourceNames.get(c.getSourceId()),
                          c.getTags(),
                          c.getCreatedAt(),
                          c.getUpdatedAt(),
                          c.isArchived()));
        });
  }

  @Transactional(readOnly = true)
  public ContactResponse get(long id) {
    return response(contact(id));
  }

  /** The tags of the workspace's active contacts, each once, sorted. */
  @Transactional(readOnly = true)
  public List<String> tags() {
    return contacts.tags(WorkspaceContext.workspaceId());
  }

  /**
   * Active contacts that look like the one being entered, at most 5: the same email in any case
   * first, then the same phone digits (at least 6; spaces, dashes, a plus aside), each by id.
   */
  @Transactional(readOnly = true)
  public List<ContactRef> duplicates(String email, String phone) {
    String sameEmail = Texts.optional(email);
    String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
    Stream<Contact> byEmail =
        sameEmail == null
            ? Stream.empty()
            : contacts.findActiveByEmail(sameEmail, Limit.of(MAX_DUPLICATES)).stream();
    Stream<Contact> byPhone =
        digits.length() < MIN_PHONE_DIGITS
            ? Stream.empty()
            : contacts.findActiveByPhoneDigits(digits, Limit.of(MAX_DUPLICATES)).stream();
    // Same persistence context, same object: distinct() lists a contact found both ways once.
    return Stream.concat(byEmail, byPhone)
        .distinct()
        .limit(MAX_DUPLICATES)
        .map(c -> new ContactRef(c.getId(), c.getFullName(), c.getEmail(), c.getPhone()))
        .toList();
  }

  public ContactResponse create(CreateContactRequest r) {
    return response(create(r, access.ownerForCreate("ownerId", r.ownerId())));
  }

  /**
   * A new contact for {@code owner}, whom the caller has decided ({@code r.ownerId()} is ignored).
   * Checks the links like any create and writes the {@code CREATE} event.
   */
  public Contact create(ContactInput r, long owner) {
    links.company(r.companyId());
    links.source(r.sourceId());
    Contact contact = new Contact();
    edit(contact, r);
    contact.changeOwner(owner);
    contacts.save(contact);
    audit.record(
        AuditAction.CREATE,
        AuditEntityType.CONTACT,
        contact.getId(),
        contact.getFullName(),
        Map.of());
    return contact;
  }

  public ContactResponse update(long id, UpdateContactRequest r) {
    Contact contact = contact(id);
    access.requireEditable(contact.getOwnerId());
    Versions.check(contact, r.version());
    Long owner = access.ownerForUpdate("ownerId", r.ownerId(), contact.getOwnerId());
    links.company(r.companyId());
    links.source(r.sourceId());
    ContactResponse before = response(contact);
    edit(contact, r);
    contact.changeOwner(owner);
    // Flush so the response carries the new version.
    ContactResponse after = response(contacts.saveAndFlush(contact));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.CONTACT, id, contact.getFullName(), changes);
    }
    return after;
  }

  public ContactResponse archive(long id) {
    return setArchived(id, true);
  }

  public ContactResponse restore(long id) {
    return setArchived(id, false);
  }

  /** Idempotent: a contact already in that state comes back unchanged, with no audit event. */
  private ContactResponse setArchived(long id, boolean archived) {
    Contact contact = contact(id);
    access.requireEditable(contact.getOwnerId());
    if (contact.isArchived() != archived) {
      if (archived) contact.archive();
      else contact.restore();
      contacts.saveAndFlush(contact);
      audit.record(
          archived ? AuditAction.ARCHIVE : AuditAction.RESTORE,
          AuditEntityType.CONTACT,
          id,
          contact.getFullName(),
          Map.of());
    }
    return response(contact);
  }

  private Contact contact(long id) {
    return contacts.findById(id).orElseThrow(() -> new NotFoundException("Contact not found"));
  }

  /**
   * Create and update apply the request alike; the owner is decided and the links are checked by
   * the caller.
   */
  private static void edit(Contact contact, ContactInput r) {
    String email = Texts.optional(r.email());
    contact.update(
        Texts.required("firstName", r.firstName()),
        Texts.optional(r.lastName()),
        email == null ? null : email.toLowerCase(Locale.ROOT),
        Texts.optional(r.phone()),
        Texts.optional(r.jobTitle()),
        r.companyId(),
        r.sourceId(),
        tags(r.tags()),
        Texts.optional(r.notes()));
  }

  /** Stripped, in lower case, each once, sorted; a blank or too long tag, or a 21st, is a 400. */
  private static List<String> tags(List<String> requested) {
    if (requested == null) return List.of();
    SortedSet<String> tags = new TreeSet<>();
    for (String value : requested) {
      String tag = Texts.optional(value);
      if (tag == null) throw new FieldValidationException("tags", "must not contain a blank tag");
      tag = tag.toLowerCase(Locale.ROOT);
      if (tag.length() > MAX_TAG_LENGTH) {
        throw new FieldValidationException(
            "tags", "must not contain a tag longer than " + MAX_TAG_LENGTH + " characters");
      }
      tags.add(tag);
      if (tags.size() > MAX_TAGS) {
        throw new FieldValidationException("tags", "must not have more than " + MAX_TAGS);
      }
    }
    return List.copyOf(tags);
  }

  /** What a page shows besides the contacts' own columns: one query each for a whole page. */
  private record Related(Map<Long, MemberRef> owners, Map<Long, Ref> companies) {
    MemberRef owner(Contact c) {
      return c.getOwnerId() == null ? null : owners.get(c.getOwnerId());
    }

    Ref company(Contact c) {
      return c.getCompanyId() == null ? null : companies.get(c.getCompanyId());
    }
  }

  private Related related(List<Contact> page) {
    return new Related(
        directory.refs(page.stream().map(Contact::getOwnerId).toList()),
        companies.findAllById(ids(page, Contact::getCompanyId)).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> new Ref(c.getId(), c.getName()))));
  }

  private static List<Long> ids(List<Contact> page, Function<Contact, Long> link) {
    return page.stream().map(link).filter(Objects::nonNull).distinct().toList();
  }

  private static ContactSummary summary(Contact c, Related r) {
    return new ContactSummary(
        c.getId(),
        c.getFirstName(),
        c.getLastName(),
        c.getFullName(),
        c.getEmail(),
        c.getPhone(),
        c.getJobTitle(),
        r.company(c),
        r.owner(c),
        c.getTags(),
        c.isArchived(),
        c.getUpdatedAt());
  }

  /** One contact: what its row shows, and its source (which only this shows). */
  private ContactResponse response(Contact c) {
    Related r = related(List.of(c));
    Ref source =
        c.getSourceId() == null
            ? null
            : sources
                .findById(c.getSourceId())
                .map(s -> new Ref(s.getId(), s.getName()))
                .orElse(null);
    return new ContactResponse(
        c.getId(),
        c.getFirstName(),
        c.getLastName(),
        c.getFullName(),
        c.getEmail(),
        c.getPhone(),
        c.getJobTitle(),
        r.company(c),
        r.owner(c),
        c.getTags(),
        c.isArchived(),
        c.getUpdatedAt(),
        source,
        c.getNotes(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getVersion());
  }
}
