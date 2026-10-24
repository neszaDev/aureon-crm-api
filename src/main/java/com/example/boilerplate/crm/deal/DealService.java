package com.example.boilerplate.crm.deal;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.entity.Versions;
import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.crm.common.Currencies;
import com.example.boilerplate.crm.common.ListParams;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.RecordLinks;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.dto.*;
import com.example.boilerplate.crm.export.CsvFile;
import com.example.boilerplate.crm.export.Exporter;
import com.example.boilerplate.crm.export.RecordRefs;
import com.example.boilerplate.crm.lead.LeadRepository;
import com.example.boilerplate.crm.settings.LeadSourceRepository;
import com.example.boilerplate.crm.settings.PipelineStage;
import com.example.boilerplate.crm.settings.PipelineStageRepository;
import com.example.boilerplate.crm.settings.StageKind;
import com.example.boilerplate.workspace.MemberDirectory;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current workspace's deals and their stage history. */
@Service
@Transactional
public class DealService {
  private static final List<String> EXPORT_HEADER =
      List.of(
          "Reference",
          "Name",
          "Amount",
          "Currency",
          "Stage",
          "Status",
          "Probability",
          "Expected close",
          "Company",
          "Contact",
          "Owner",
          "Closed",
          "Lost reason",
          "Created",
          "Updated",
          "Archived");

  private final DealRepository deals;
  private final DealStageChangeRepository history;
  private final PipelineStageRepository stages;
  private final CompanyRepository companies;
  private final ContactRepository contacts;
  private final LeadSourceRepository sources;
  private final LeadRepository leads;
  private final WorkspaceRepository workspaces;
  private final RecordLinks links;
  private final WorkspaceAccess access;
  private final MemberDirectory directory;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;
  private final Exporter exporter;

  public DealService(
      DealRepository deals,
      DealStageChangeRepository history,
      PipelineStageRepository stages,
      CompanyRepository companies,
      ContactRepository contacts,
      LeadSourceRepository sources,
      LeadRepository leads,
      WorkspaceRepository workspaces,
      RecordLinks links,
      WorkspaceAccess access,
      MemberDirectory directory,
      AuditTrail audit,
      AuditChanges auditChanges,
      Exporter exporter) {
    this.deals = deals;
    this.history = history;
    this.stages = stages;
    this.companies = companies;
    this.contacts = contacts;
    this.sources = sources;
    this.leads = leads;
    this.workspaces = workspaces;
    this.links = links;
    this.access = access;
    this.directory = directory;
    this.audit = audit;
    this.auditChanges = auditChanges;
    this.exporter = exporter;
  }

  @Transactional(readOnly = true)
  public Page<DealSummary> list(
      DealFilter filter, int page, int size, DealSort sort, Direction direction) {
    Specification<Deal> matching = DealSpecifications.matching(filter);
    Specification<Deal> ordering = DealSpecifications.ordering(sort, direction);
    Page<Deal> found =
        ordering == null
            ? deals.findAll(matching, ListParams.pageable(page, size, direction, sort))
            : deals.findAll(matching.and(ordering), ListParams.unsorted(page, size));
    Related related = related(found.getContent(), true);
    return found.map(d -> summary(d, related));
  }

  /** The list's deals (its filter, its default order) as CSV (api.md, Export). */
  public CsvFile export(DealFilter filter) {
    return exporter.export(
        "deals",
        filter,
        EXPORT_HEADER,
        deals,
        DealSpecifications.matching(filter),
        ListParams.sort(DealSort.UPDATED_AT, Direction.DESC),
        found -> {
          Related r = related(found, false);
          return found.stream()
              .map(
                  d -> {
                    PipelineStage stage = r.stage(d);
                    return Exporter.row(
                        RecordRefs.format('D', d.getId()),
                        d.getName(),
                        d.getAmount(),
                        d.getCurrency(),
                        stage.getName(),
                        DealStatus.of(stage.getKind()),
                        probability(d, stage),
                        d.getExpectedCloseDate(),
                        r.company(d),
                        r.contact(d),
                        r.owner(d),
                        d.getClosedAt(),
                        d.getLostReason(),
                        d.getCreatedAt(),
                        d.getUpdatedAt(),
                        d.isArchived());
                  });
        });
  }

  @Transactional(readOnly = true)
  public DealResponse get(long id) {
    return response(deal(id));
  }

  /** The stage history, newest first. */
  @Transactional(readOnly = true)
  public List<StageChangeResponse> history(long id) {
    deal(id);
    List<DealStageChange> changes = history.findByDealIdOrderByChangedAtDescIdDesc(id);
    Map<Long, MemberRef> people =
        directory.refs(changes.stream().map(DealStageChange::getChangedBy).toList());
    return changes.stream()
        .map(
            c ->
                new StageChangeResponse(
                    c.getFromStageName() == null
                        ? null
                        : new HistoryStageRef(c.getFromStageId(), c.getFromStageName()),
                    new HistoryStageRef(c.getToStageId(), c.getToStageName()),
                    c.getChangedBy() == null ? null : people.get(c.getChangedBy()),
                    c.getChangedAt()))
        .toList();
  }

  public DealResponse create(CreateDealRequest r) {
    return response(create(r, access.ownerForCreate("ownerId", r.ownerId()), null));
  }

  /**
   * A new deal for {@code owner}, whom the caller has decided ({@code r.ownerId()} is ignored),
   * from the converted lead {@code leadId} or null. Checks the stage and the links like any create
   * and writes the first history entry and the {@code CREATE} event.
   */
  public Deal create(CreateDealRequest r, long owner, Long leadId) {
    PipelineStage stage = r.stageId() == null ? firstOpenStage() : stage(r.stageId());
    if (stage.getKind() != StageKind.OPEN) {
      throw new FieldValidationException("stageId", "must be an open stage");
    }
    checkLinks(r);
    String currency = currency(r);
    Deal deal = new Deal(stage.getId(), leadId);
    edit(deal, r, currency);
    deal.changeOwner(owner);
    deals.save(deal);
    history.save(new DealStageChange(deal.getId(), null, stage, caller()));
    audit.record(AuditAction.CREATE, AuditEntityType.DEAL, deal.getId(), deal.getName(), Map.of());
    return deal;
  }

  public DealResponse update(long id, UpdateDealRequest r) {
    Deal deal = deal(id);
    access.requireEditable(deal.getOwnerId());
    Versions.check(deal, r.version());
    Long owner = access.ownerForUpdate("ownerId", r.ownerId(), deal.getOwnerId());
    checkLinks(r);
    String currency = currency(r);
    DealResponse before = response(deal);
    edit(deal, r, currency);
    deal.changeOwner(owner);
    // Flush so the response carries the new version.
    DealResponse after = response(deals.saveAndFlush(deal));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!changes.isEmpty()) {
      audit.record(AuditAction.UPDATE, AuditEntityType.DEAL, id, deal.getName(), changes);
    }
    return after;
  }

  /**
   * Moves the deal to another stage of the workspace, with a history row and a {@code STAGE_CHANGE}
   * audit event. LOST needs a reason, which any other stage clears. The current stage again changes
   * nothing, except LOST with a different reason, which saves the reason (no history row: the stage
   * stays).
   */
  public DealResponse move(long id, MoveDealRequest r) {
    Deal deal = deal(id);
    access.requireEditable(deal.getOwnerId());
    // Before the version check: these don't depend on what the client last read.
    PipelineStage target = stage(r.stageId());
    boolean sameStage = target.getId().equals(deal.getStageId());
    String reason = Texts.optional(r.lostReason());
    if (target.getKind() == StageKind.LOST && reason == null && !sameStage) {
      throw new FieldValidationException("lostReason", "is required to mark a deal lost");
    }
    Versions.check(deal, r.version());
    boolean newReason =
        target.getKind() == StageKind.LOST
            && reason != null
            && !reason.equals(deal.getLostReason());
    if (sameStage && !newReason) return response(deal);
    PipelineStage from = stages.findById(deal.getStageId()).orElseThrow();
    DealResponse before = response(deal);
    deal.moveTo(target.getId(), from.getKind(), target.getKind(), reason);
    // Before the response: its stageEnteredAt is this entry's time.
    if (!sameStage) history.save(new DealStageChange(id, from, target, caller()));
    DealResponse after = response(deals.saveAndFlush(deal));
    Map<String, Map<String, Object>> changes = auditChanges.recordDiff(before, after);
    if (!sameStage) {
      // The names, not the stage references: what a reader of the trail needs.
      changes.put("stage", Map.<String, Object>of("from", from.getName(), "to", target.getName()));
    }
    audit.record(AuditAction.STAGE_CHANGE, AuditEntityType.DEAL, id, deal.getName(), changes);
    return after;
  }

  public DealResponse archive(long id) {
    return setArchived(id, true);
  }

  public DealResponse restore(long id) {
    return setArchived(id, false);
  }

  /** Idempotent: a deal already in that state comes back unchanged, with no audit event. */
  private DealResponse setArchived(long id, boolean archived) {
    Deal deal = deal(id);
    access.requireEditable(deal.getOwnerId());
    if (deal.isArchived() != archived) {
      if (archived) deal.archive();
      else deal.restore();
      deals.saveAndFlush(deal);
      audit.record(
          archived ? AuditAction.ARCHIVE : AuditAction.RESTORE,
          AuditEntityType.DEAL,
          id,
          deal.getName(),
          Map.of());
    }
    return response(deal);
  }

  /** The board's cards: one {@link Related} for all of them, in the given order. */
  List<DealSummary> summaries(List<Deal> page) {
    Related related = related(page, true);
    return page.stream().map(d -> summary(d, related)).toList();
  }

  private Deal deal(long id) {
    return deals.findById(id).orElseThrow(() -> new NotFoundException("Deal not found"));
  }

  /**
   * A stage of this workspace (the tenant filter hides the others), or a 400 on {@code stageId}.
   */
  private PipelineStage stage(long id) {
    return stages
        .findById(id)
        .orElseThrow(
            () -> new FieldValidationException("stageId", "is not found in this workspace"));
  }

  /** There is always one: the last open stage can't be deleted. */
  private PipelineStage firstOpenStage() {
    return stages.findAllByOrderByPositionAscIdAsc().stream()
        .filter(s -> s.getKind() == StageKind.OPEN)
        .findFirst()
        .orElseThrow();
  }

  private void checkLinks(DealInput r) {
    links.company(r.companyId());
    links.contact(r.contactId());
    links.source(r.sourceId());
  }

  /** The request's currency (upper-cased, checked) or the workspace's. */
  private String currency(DealInput r) {
    String workspaceDefault =
        workspaces.findById(WorkspaceContext.workspaceId()).orElseThrow().getDefaultCurrency();
    return Currencies.resolve(Texts.optional(r.currency()), workspaceDefault);
  }

  private static long caller() {
    return WorkspaceContext.require().userId();
  }

  /**
   * Create and update apply the request alike; the stage, the owner and the links are handled by
   * the caller.
   */
  private static void edit(Deal deal, DealInput r, String currency) {
    deal.update(
        Texts.required("name", r.name()),
        r.amount(),
        currency,
        r.probability(),
        r.expectedCloseDate(),
        r.companyId(),
        r.contactId(),
        r.sourceId(),
        Texts.optional(r.notes()));
  }

  /**
   * What a page shows besides the deals' own columns: one query each for a whole page ({@code
   * people}: the owners and the signers together).
   */
  private record Related(
      Map<Long, MemberRef> people,
      Map<Long, Ref> companies,
      Map<Long, PersonRef> contacts,
      Map<Long, PipelineStage> stages,
      Map<Long, Instant> stageEnteredAt,
      Map<Long, List<StageSignature>> signatures) {
    MemberRef owner(Deal d) {
      return d.getOwnerId() == null ? null : people.get(d.getOwnerId());
    }

    Ref company(Deal d) {
      return d.getCompanyId() == null ? null : companies.get(d.getCompanyId());
    }

    PersonRef contact(Deal d) {
      return d.getContactId() == null ? null : contacts.get(d.getContactId());
    }

    PipelineStage stage(Deal d) {
      return stages.get(d.getStageId());
    }

    Instant stageEnteredAt(Deal d) {
      return stageEnteredAt.get(d.getId());
    }

    List<StageSignature> signatures(Deal d) {
      return signatures.getOrDefault(d.getId(), List.of());
    }
  }

  /** {@code signatures}: the stage signatures too, which only the summaries show. */
  private Related related(List<Deal> page, boolean signatures) {
    if (page.isEmpty()) {
      return new Related(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
    List<Object[]> signed =
        signatures
            ? history.signatures(WorkspaceContext.workspaceId(), ids(page, Deal::getId))
            : List.of();
    // One member query for the owners and the signers.
    Map<Long, MemberRef> people =
        directory.refs(
            Stream.concat(
                    page.stream().map(Deal::getOwnerId), signed.stream().map(row -> (Long) row[2]))
                .toList());
    return new Related(
        people,
        companies.findAllById(ids(page, Deal::getCompanyId)).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> new Ref(c.getId(), c.getName()))),
        contacts.findAllById(ids(page, Deal::getContactId)).stream()
            .collect(
                Collectors.toMap(c -> c.getId(), c -> new PersonRef(c.getId(), c.getFullName()))),
        stages.findAllById(ids(page, Deal::getStageId)).stream()
            .collect(Collectors.toMap(s -> s.getId(), Function.identity())),
        history.stageEnteredAt(ids(page, Deal::getId)).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Instant) row[1])),
        signatures(signed, people));
  }

  /**
   * Per deal, who moved it into each stage: the rows of {@link
   * DealStageChangeRepository#signatures}, the signers among {@code people}.
   */
  private static Map<Long, List<StageSignature>> signatures(
      List<Object[]> rows, Map<Long, MemberRef> people) {
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                row -> (Long) row[0],
                Collectors.mapping(
                    row ->
                        new StageSignature(
                            (Long) row[1], row[2] == null ? null : people.get((Long) row[2])),
                    Collectors.toList())));
  }

  private static List<Long> ids(List<Deal> page, Function<Deal, Long> link) {
    return page.stream().map(link).filter(Objects::nonNull).distinct().toList();
  }

  private static DealSummary summary(Deal d, Related r) {
    PipelineStage stage = r.stage(d);
    return new DealSummary(
        d.getId(),
        d.getName(),
        d.getAmount(),
        d.getCurrency(),
        new StageRef(stage.getId(), stage.getName(), stage.getKind()),
        r.stageEnteredAt(d),
        r.signatures(d),
        DealStatus.of(stage.getKind()),
        probability(d, stage),
        d.getExpectedCloseDate(),
        r.owner(d),
        r.company(d),
        r.contact(d),
        d.getClosedAt(),
        d.isArchived(),
        d.getUpdatedAt(),
        d.getVersion());
  }

  private DealResponse response(Deal d) {
    Related r = related(List.of(d), false);
    PipelineStage stage = r.stage(d);
    Ref source =
        d.getSourceId() == null
            ? null
            : sources
                .findById(d.getSourceId())
                .map(s -> new Ref(s.getId(), s.getName()))
                .orElse(null);
    PersonRef lead =
        d.getLeadId() == null
            ? null
            : leads
                .findById(d.getLeadId())
                .map(l -> new PersonRef(l.getId(), l.getFullName()))
                .orElse(null);
    return new DealResponse(
        d.getId(),
        d.getName(),
        d.getAmount(),
        d.getCurrency(),
        new StageRef(stage.getId(), stage.getName(), stage.getKind()),
        r.stageEnteredAt(d),
        DealStatus.of(stage.getKind()),
        probability(d, stage),
        d.getExpectedCloseDate(),
        r.owner(d),
        r.company(d),
        r.contact(d),
        d.getClosedAt(),
        d.isArchived(),
        d.getUpdatedAt(),
        d.getVersion(),
        d.getProbability(),
        source,
        d.getLostReason(),
        lead,
        d.getNotes(),
        d.getArchivedAt(),
        d.getCreatedAt());
  }

  /** The deal's own probability, or else its stage's. */
  private static int probability(Deal d, PipelineStage stage) {
    return d.getProbability() != null ? d.getProbability() : stage.getProbability();
  }
}
