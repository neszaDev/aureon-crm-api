package com.example.boilerplate.crm.company;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.company.dto.*;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current workspace's companies. */
@Service
@Transactional
public class CompanyService {
  private static final int MAX_DUPLICATES = 5;
  private static final int MIN_HOST_LENGTH = 3;

  private final CompanyRepository companies;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;

  public CompanyService(
      CompanyRepository companies,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges) {
    this.companies = companies;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
  }

  @Transactional(readOnly = true)
  public Page<CompanySummary> list(
      CompanyFilter filter, int page, int size, CompanySort sort, Direction direction) {
    Page<Company> found =
        companies.findAll(
            CompanySpecifications.matching(filter),
            ListParams.pageable(page, size, direction, sort));
    Related related = related(found.getContent());
    return found.map(c -> summary(c, related));
  }

  @Transactional(readOnly = true)
  public CompanyResponse get(long id) {
    return response(company(id));
  }

  /**
   * Active companies that look like the one being entered, at most 5: the same name in any case
   * first, then the same website host ({@code www.} and case aside), each by id.
   */
  @Transactional(readOnly = true)
  public List<CompanyRef> duplicates(String name, String website) {
    String sameName = Texts.optional(name);
    String host = host(website);
    Stream<Company> byName =
        sameName == null
            ? Stream.empty()
            : companies.findActiveByName(sameName, Limit.of(MAX_DUPLICATES)).stream();
    // The LIKE only narrows the candidates down; the host itself is compared here.
    Stream<Company> byHost =
        host == null
            ? Stream.empty()
            : companies.findWebsiteCandidates(host).stream()
                .filter(c -> host.equals(host(c.getWebsite())));
    // Same persistence context, same object: distinct() lists a company found both ways once.
    return Stream.concat(byName, byHost)
        .distinct()
        .limit(MAX_DUPLICATES)
        .map(c -> new CompanyRef(c.getId(), c.getName(), c.getWebsite()))
        .toList();
  }

  public CompanyResponse create(CreateCompanyRequest r) {
    long owner = access.ownerForCreate("ownerId", r.ownerId());
    Company company = new Company();
    edit(company, r);
    company.changeOwner(owner);
    companies.save(company);
    audit.record(
        AuditAction.CREATE, AuditEntityType.COMPANY, company.getId(), company.getName(), Map.of());
    return response(company);
  }

  public CompanyResponse update(long id, UpdateCompanyRequest r) {
    Company company = company(id);
    access.requireEditable(company.getOwnerId());
    Versions.check(company, r.version());
    Long owner = access.ownerForUpdate("ownerId", r.ownerId(), company.getOwnerId());
    CompanyResponse before = response(company);
    edit(company, r);
    company.changeOwner(owner);
    // Flush so the response carries the new version.
    CompanyResponse after = response(companies.saveAndFlush(company));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.COMPANY, id, company.getName(), changes);
    }
    return after;
  }

  public CompanyResponse archive(long id) {
    return setArchived(id, true);
  }

  public CompanyResponse restore(long id) {
    return setArchived(id, false);
  }

  /** Idempotent: a company already in that state comes back unchanged, with no audit event. */
  private CompanyResponse setArchived(long id, boolean archived) {
    Company company = company(id);
    access.requireEditable(company.getOwnerId());
    if (company.isArchived() != archived) {
      if (archived) company.archive();
      else company.restore();
      companies.saveAndFlush(company);
      audit.record(
          archived ? AuditAction.ARCHIVE : AuditAction.RESTORE,
          AuditEntityType.COMPANY,
          id,
          company.getName(),
          Map.of());
    }
    return response(company);
  }

  private Company company(long id) {
    return companies.findById(id).orElseThrow(() -> new NotFoundException("Company not found"));
  }

  /** Create and update apply the request alike; the owner is decided by the caller. */
  private static void edit(Company company, CompanyInput r) {
    company.update(
        Texts.required("name", r.name()),
        Texts.optional(r.industry()),
        Texts.optional(r.website()),
        Texts.optional(r.phone()),
        Texts.optional(r.address()),
        r.employeeCount(),
        Texts.optional(r.notes()));
  }

  /**
   * The website's host in lower case without a leading {@code www.}; null without a website or when
   * it doesn't parse.
   */
  private static String host(String website) {
    String text = Texts.optional(website);
    if (text == null) return null;
    try {
      String host = new URI(text).getHost();
      if (host == null) return null;
      host = host.toLowerCase(Locale.ROOT);
      if (host.startsWith("www.")) host = host.substring(4);
      // "" (from "https://www.") or "ab" would make the candidate LIKE match nearly every website.
      return host.length() < MIN_HOST_LENGTH ? null : host;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  /** What responses show besides the company's own columns: one query each for a whole page. */
  private record Related(
      Map<Long, MemberRef> owners, Map<Long, Long> contactCounts, Map<Long, Long> openDealCounts) {
    MemberRef owner(Company c) {
      return c.getOwnerId() == null ? null : owners.get(c.getOwnerId());
    }

    long contacts(Company c) {
      return contactCounts.getOrDefault(c.getId(), 0L);
    }

    long openDeals(Company c) {
      return openDealCounts.getOrDefault(c.getId(), 0L);
    }
  }

  private Related related(List<Company> page) {
    List<Long> ids = page.stream().map(Company::getId).toList();
    return new Related(
        directory.refs(page.stream().map(Company::getOwnerId).toList()),
        contactCounts(ids),
        openDealCounts(ids));
  }

  // Task 3 (contacts) replaces this with one grouped count of the companies' active contacts.
  private Map<Long, Long> contactCounts(List<Long> companyIds) {
    return Map.of();
  }

  // Task 5 (deals) replaces this with one grouped count of their active deals in OPEN stages.
  private Map<Long, Long> openDealCounts(List<Long> companyIds) {
    return Map.of();
  }

  private CompanyResponse response(Company c) {
    return response(c, related(List.of(c)));
  }

  private static CompanySummary summary(Company c, Related r) {
    return new CompanySummary(
        c.getId(),
        c.getName(),
        c.getIndustry(),
        c.getWebsite(),
        c.getPhone(),
        r.owner(c),
        r.contacts(c),
        r.openDeals(c),
        c.isArchived(),
        c.getUpdatedAt());
  }

  private static CompanyResponse response(Company c, Related r) {
    return new CompanyResponse(
        c.getId(),
        c.getName(),
        c.getIndustry(),
        c.getWebsite(),
        c.getPhone(),
        r.owner(c),
        r.contacts(c),
        r.openDeals(c),
        c.isArchived(),
        c.getUpdatedAt(),
        c.getAddress(),
        c.getEmployeeCount(),
        c.getNotes(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getVersion());
  }
}
