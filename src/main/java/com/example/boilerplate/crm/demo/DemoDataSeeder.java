package com.example.boilerplate.crm.demo;

import static com.example.boilerplate.workspace.WorkspaceRole.ADMIN;
import static com.example.boilerplate.workspace.WorkspaceRole.MANAGER;
import static com.example.boilerplate.workspace.WorkspaceRole.OWNER;
import static com.example.boilerplate.workspace.WorkspaceRole.SALES_REP;

import com.example.boilerplate.crm.activity.ActivityService;
import com.example.boilerplate.crm.activity.ActivityType;
import com.example.boilerplate.crm.activity.dto.CreateActivityRequest;
import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.company.CompanyService;
import com.example.boilerplate.crm.company.dto.CreateCompanyRequest;
import com.example.boilerplate.crm.contact.ContactService;
import com.example.boilerplate.crm.contact.dto.CreateContactRequest;
import com.example.boilerplate.crm.deal.DealService;
import com.example.boilerplate.crm.deal.dto.CreateDealRequest;
import com.example.boilerplate.crm.deal.dto.DealResponse;
import com.example.boilerplate.crm.deal.dto.MoveDealRequest;
import com.example.boilerplate.crm.lead.LeadConversionService;
import com.example.boilerplate.crm.lead.LeadService;
import com.example.boilerplate.crm.lead.LeadStatus;
import com.example.boilerplate.crm.lead.dto.CreateLeadRequest;
import com.example.boilerplate.crm.lead.dto.LeadConversionInput;
import com.example.boilerplate.crm.lead.dto.LeadConversionInput.DealDraft;
import com.example.boilerplate.crm.lead.dto.LeadConversionResponse;
import com.example.boilerplate.crm.lead.dto.LeadResponse;
import com.example.boilerplate.crm.lead.dto.LeadStatusRequest;
import com.example.boilerplate.crm.settings.PipelineStageService;
import com.example.boilerplate.crm.settings.StageKind;
import com.example.boilerplate.crm.settings.dto.StageResponse;
import com.example.boilerplate.crm.task.TaskService;
import com.example.boilerplate.crm.task.dto.CreateTaskRequest;
import com.example.boilerplate.crm.task.dto.TaskResponse;
import com.example.boilerplate.crm.task.dto.VersionRequest;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceMember;
import com.example.boilerplate.workspace.WorkspaceMemberRepository;
import com.example.boilerplate.workspace.WorkspaceRole;
import com.example.boilerplate.workspace.WorkspaceService;
import com.example.boilerplate.workspace.dto.CreateWorkspaceRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The demo workspace (docs/crm/README.md, Try it): with {@code app.demo.enabled} ({@code
 * DEMO_DATA}), at startup, once, in the local and dev profiles only, never together with prod. Four
 * members, one per role, and fictional records written through the services as the owner
 * (activities as their authors), so they follow the same rules and leave the same audit events as
 * any. Then the creation and stage history of 21 of the 24 deals are spread over the last 180 days
 * by plain SQL (the three made by lead conversions keep their real times); the audit trail keeps
 * the real times (it is append-only).
 *
 * <p>Not {@code @Transactional}: {@link WorkspaceContext#runAs} refuses to run inside a
 * transaction, so a failure part-way leaves what was written; {@code make db-reset} starts over.
 */
@Component
// An expression, not {"local", "dev"}: that is an OR, which "prod,dev" would satisfy.
@Profile("(local | dev) & !prod")
public class DemoDataSeeder implements ApplicationRunner {
  static final String WORKSPACE = "Northwind Supply (demo)";
  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
  private static final List<String> LOGINS =
      List.of("owner@demo.test", "admin@demo.test", "manager@demo.test", "rep@demo.test");
  private static final List<String> NAMES =
      List.of("Avery Owner", "Sam Admin", "Morgan Manager", "Riley Rep");
  private static final List<WorkspaceRole> ROLES = List.of(OWNER, ADMIN, MANAGER, SALES_REP);
  private static final List<String> COMPANIES =
      List.of(
          "Blue Harbor Logistics",
          "Cedar Ridge Foods",
          "Lumen Works",
          "Northpeak Outfitters",
          "Orchid Bay Hotels",
          "Pinecrest Clinics",
          "Quarry Lane Builders",
          "Riverstone Textiles",
          "Silver Fern Software",
          "Tidewater Marine",
          "Umber Hill Coffee",
          "Willow Creek Farms");
  private static final List<String> INDUSTRIES =
      List.of("Logistics", "Food", "Manufacturing", "Retail", "Hospitality", "Healthcare");
  private static final List<String> FIRST_NAMES =
      List.of(
          "Ava", "Ben", "Chloe", "Dan", "Ella", "Finn", "Grace", "Hugo", "Isla", "Jack", "Kira",
          "Leo", "Maya", "Noah", "Olive");
  private static final List<String> LAST_NAMES =
      List.of("Archer", "Brooks", "Carter", "Dalton", "Ellis", "Foster", "Gray", "Hayes");
  private static final List<String> LEAD_LAST_NAMES =
      List.of("Ivers", "Jensen", "Kemp", "Lowell", "Marsh");
  private static final List<String> PROSPECTS =
      List.of(
          "Amber Lane Studio",
          "Brightwater Labs",
          "Copperleaf Dental",
          "Driftwood Media",
          "Evergreen Schools");
  private static final List<String> JOB_TITLES =
      List.of("Purchasing Manager", "Operations Director", "CEO", "Office Manager", "CFO");
  private static final List<String> DEAL_KINDS =
      List.of("annual plan", "pilot", "expansion", "renewal", "onboarding");
  private static final List<String> TASKS =
      List.of(
          "Send the proposal",
          "Follow up on pricing",
          "Book a demo",
          "Check the contract terms",
          "Call about the renewal");
  // Subjects k and k + 4 suit ActivityType.values()[k]: CALL, EMAIL, MEETING, NOTE.
  private static final List<String> SUBJECTS =
      List.of(
          "Discovery call",
          "Sent the price list",
          "Product demo",
          "Budget confirmed for next quarter",
          "Check-in call",
          "Shared a case study",
          "Site visit",
          "Prefers a monthly plan");

  private final DemoProperties properties;
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final WorkspaceService workspaces;
  private final WorkspaceMemberRepository members;
  private final PipelineStageService stages;
  private final CompanyService companies;
  private final ContactService contacts;
  private final LeadService leads;
  private final LeadConversionService conversions;
  private final DealService deals;
  private final TaskService tasks;
  private final ActivityService activities;
  private final JdbcTemplate jdbc;

  public DemoDataSeeder(
      DemoProperties properties,
      UserRepository users,
      PasswordEncoder encoder,
      WorkspaceService workspaces,
      WorkspaceMemberRepository members,
      PipelineStageService stages,
      CompanyService companies,
      ContactService contacts,
      LeadService leads,
      LeadConversionService conversions,
      DealService deals,
      TaskService tasks,
      ActivityService activities,
      JdbcTemplate jdbc) {
    this.properties = properties;
    this.users = users;
    this.encoder = encoder;
    this.workspaces = workspaces;
    this.members = members;
    this.stages = stages;
    this.companies = companies;
    this.contacts = contacts;
    this.leads = leads;
    this.conversions = conversions;
    this.deals = deals;
    this.tasks = tasks;
    this.activities = activities;
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) return;
    String password = properties.password();
    if (password == null || password.length() < 6 || password.length() > 72)
      // Never include the value: this message ends up in startup logs.
      throw new IllegalStateException(
          "app.demo.password (DEMO_PASSWORD) must be 6 to 72 characters when DEMO_DATA is true");
    List<Long> found =
        jdbc.queryForList(
            "SELECT id FROM workspaces WHERE name = ? ORDER BY id LIMIT 1", Long.class, WORKSPACE);
    long workspaceId = found.isEmpty() ? seed(password) : found.get(0);
    List<String> logins = LOGINS.stream().filter(email -> opens(email, password)).toList();
    log.info(
        "Demo workspace {}: sign in as {} with DEMO_PASSWORD",
        workspaceId,
        String.join(", ", logins));
  }

  /**
   * Whether {@code password} opens the login: an account that existed before the seeding keeps its
   * own password, and DEMO_PASSWORD may have changed since; a warning names only the email.
   */
  private boolean opens(String email, String password) {
    boolean opens =
        users
            .findByEmail(email)
            .filter(u -> encoder.matches(password, u.getPasswordHash()))
            .isPresent();
    if (!opens) log.warn("Demo login {} does not take DEMO_PASSWORD (another password)", email);
    return opens;
  }

  private long seed(String password) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < LOGINS.size(); i++) ids.add(user(LOGINS.get(i), NAMES.get(i), password));
    long w =
        workspaces
            .create(LOGINS.get(0), new CreateWorkspaceRequest(WORKSPACE, "USD", ZONE.getId()))
            .id();
    List<Membership> team = new ArrayList<>();
    for (int i = 0; i < LOGINS.size(); i++) {
      // As the ITs add members; invitations are the real way in.
      if (i > 0) members.save(new WorkspaceMember(w, ids.get(i), ROLES.get(i)));
      team.add(new Membership(w, ids.get(i), LOGINS.get(i), ROLES.get(i)));
    }
    WorkspaceContext.runAs(team.get(0), () -> records(team, new Random(42)));
    return w;
  }

  /** The account of {@code email}, created with {@code name} unless it exists. */
  private long user(String email, String name, String password) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> {
              User user = new User(email, encoder.encode(password));
              user.changeDisplayName(name);
              return users.save(user);
            })
        .getId();
  }

  private Void records(List<Membership> team, Random random) {
    Instant now = Instant.now();
    LocalDate today = LocalDate.now(ZONE);
    long w = team.get(0).workspaceId();
    List<Long> people = team.stream().map(Membership::userId).toList();

    List<Long> companyIds = new ArrayList<>();
    for (int i = 0; i < COMPANIES.size(); i++) {
      companyIds.add(
          companies
              .create(
                  new CreateCompanyRequest(
                      COMPANIES.get(i),
                      INDUSTRIES.get(i % INDUSTRIES.size()),
                      null,
                      null,
                      null,
                      10 + random.nextInt(490),
                      pick(people, random),
                      null))
              .id());
    }

    // 27 here and 3 from the converted leads; contact i works at company i % 12.
    List<Long> contactIds = new ArrayList<>();
    for (int i = 0; i < 27; i++) {
      String first = FIRST_NAMES.get(i % FIRST_NAMES.size());
      String last = LAST_NAMES.get(i % LAST_NAMES.size());
      List<String> tags = new ArrayList<>();
      if (i % 5 == 0) tags.add("vip");
      if (i % 4 == 1) tags.add("partner");
      contactIds.add(
          contacts
              .create(
                  new CreateContactRequest(
                      first,
                      last,
                      email(first, last),
                      null,
                      JOB_TITLES.get(i % JOB_TITLES.size()),
                      companyIds.get(i % companyIds.size()),
                      pick(people, random),
                      null,
                      tags,
                      null))
              .id());
    }

    // 21 here and 3 from the converted leads; deal i is with contact i and their company.
    List<Long> dealIds = new ArrayList<>();
    Set<Long> convertedDeals = new HashSet<>();
    for (int i = 0; i < 21; i++) {
      dealIds.add(
          deals
              .create(
                  new CreateDealRequest(
                      COMPANIES.get(i % COMPANIES.size())
                          + " "
                          + DEAL_KINDS.get(i % DEAL_KINDS.size()),
                      amount(random),
                      i % 5 == 3 ? "EUR" : null,
                      null,
                      null,
                      today.plusDays(15 + random.nextInt(90)),
                      pick(people, random),
                      companyIds.get(i % companyIds.size()),
                      contactIds.get(i),
                      null,
                      null))
              .id());
    }

    // Three in each status: NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED.
    for (int i = 0; i < 15; i++) {
      LeadStatus target = LeadStatus.values()[i % 5];
      String first = FIRST_NAMES.get(i);
      String last = LEAD_LAST_NAMES.get(i % LEAD_LAST_NAMES.size());
      LeadResponse lead =
          leads.create(
              new CreateLeadRequest(
                  first,
                  last,
                  PROSPECTS.get(i % PROSPECTS.size()),
                  email(first, last),
                  null,
                  JOB_TITLES.get(i % JOB_TITLES.size()),
                  null,
                  pick(people, random),
                  Priority.values()[i % 3],
                  today.plusDays(random.nextInt(21) - 7),
                  amount(random),
                  null,
                  null));
      if (target != LeadStatus.NEW) lead = status(lead, LeadStatus.CONTACTED, null);
      if (target == LeadStatus.QUALIFIED || target == LeadStatus.CONVERTED) {
        lead = status(lead, LeadStatus.QUALIFIED, null);
      }
      if (target == LeadStatus.UNQUALIFIED) {
        lead = status(lead, LeadStatus.UNQUALIFIED, "No budget this year");
      }
      if (target == LeadStatus.CONVERTED) {
        LeadConversionResponse converted =
            conversions.convert(
                lead.id(),
                new LeadConversionInput(
                    companyIds.get(i % companyIds.size()),
                    false,
                    null,
                    new DealDraft(
                        lead.companyName() + " first order",
                        amount(random),
                        null,
                        null,
                        today.plusDays(30))));
        contactIds.add(converted.contact().id());
        dealIds.add(converted.deal().id());
        convertedDeals.add(converted.deal().id());
      }
    }

    // Deal i ends in stage i % 6 (four in each) through the open stages before it; a lost deal
    // drops out after a random one. The first six, one per stage, are under 30 days old, so the
    // dashboard's default 30 days show a won and a lost deal. The deals of the converted leads
    // keep their real times: back-dated, they would predate their lead and its conversion.
    Map<Long, Instant> dealCreated = new HashMap<>();
    List<StageResponse> pipeline = stages.list();
    List<StageResponse> open = pipeline.stream().filter(s -> s.kind() == StageKind.OPEN).toList();
    for (int i = 0; i < dealIds.size(); i++) {
      StageResponse target = pipeline.get(i % pipeline.size());
      int reached =
          switch (target.kind()) {
            case OPEN -> open.indexOf(target);
            case WON -> open.size() - 1;
            case LOST -> random.nextInt(open.size());
          };
      DealResponse deal = deals.get(dealIds.get(i));
      for (StageResponse stage : open.subList(1, reached + 1)) deal = move(deal, stage, null);
      if (target.kind() == StageKind.WON) deal = move(deal, target, null);
      if (target.kind() == StageKind.LOST) deal = move(deal, target, "Chose another supplier");
      if (convertedDeals.contains(deal.id())) continue;
      Instant created =
          now.minus(Duration.ofDays(10 + random.nextInt(i < pipeline.size() ? 20 : 170)))
              .minus(Duration.ofMinutes(random.nextInt(600)))
              .truncatedTo(ChronoUnit.SECONDS);
      backdate(w, deal, created, now);
      dealCreated.put(deal.id(), created);
    }

    // A quarter each: completed, overdue, due within the week, due later.
    for (int i = 0; i < 30; i++) {
      Instant due =
          switch (i % 4) {
            case 0 -> now.minus(Duration.ofDays(1 + random.nextInt(30)));
            case 1 -> now.minus(Duration.ofHours(2 + random.nextInt(24 * 14)));
            case 2 -> now.plus(Duration.ofHours(2 + random.nextInt(24 * 6)));
            default -> now.plus(Duration.ofDays(10 + random.nextInt(30)));
          };
      TaskResponse task =
          tasks.create(
              new CreateTaskRequest(
                  TASKS.get(i % TASKS.size()),
                  null,
                  due,
                  Priority.values()[i % 3],
                  pick(people, random),
                  null,
                  null,
                  null,
                  dealIds.get(i % dealIds.size())));
      if (i % 4 == 0) tasks.complete(task.id(), new VersionRequest(task.version()));
    }

    // Over the last 60 days (never before the deal they are on), on the back-dated deals and on
    // contacts, each by a random member: type, author and record drawn apart.
    Instant until = now.minus(Duration.ofMinutes(30));
    List<Long> dated = dealIds.stream().filter(dealCreated::containsKey).toList();
    for (int i = 0; i < 60; i++) {
      ActivityType type = ActivityType.values()[random.nextInt(4)];
      Long dealId = random.nextBoolean() ? dated.get(random.nextInt(dated.size())) : null;
      Instant from = now.minus(Duration.ofDays(60));
      if (dealId != null && dealCreated.get(dealId).isAfter(from)) from = dealCreated.get(dealId);
      CreateActivityRequest activity =
          new CreateActivityRequest(
              type,
              SUBJECTS.get(type.ordinal() + 4 * random.nextInt(2)),
              null,
              from.plusSeconds(random.nextLong(Duration.between(from, until).toSeconds())),
              null,
              dealId == null ? contactIds.get(random.nextInt(contactIds.size())) : null,
              null,
              dealId);
      Membership author = team.get(random.nextInt(team.size()));
      WorkspaceContext.runAs(author, () -> activities.create(activity));
    }
    return null;
  }

  private LeadResponse status(LeadResponse lead, LeadStatus status, String reason) {
    return leads.changeStatus(lead.id(), new LeadStatusRequest(status, reason, lead.version()));
  }

  private DealResponse move(DealResponse deal, StageResponse stage, String lostReason) {
    return deals.move(deal.id(), new MoveDealRequest(stage.id(), deal.version(), lostReason));
  }

  /**
   * Moves the deal's creation to {@code created} and spreads its stage changes evenly from there
   * towards {@code now}, in their order, so the first is the creation's; a closed deal's {@code
   * closedAt} becomes its last change, the move into Won or Lost, as {@link DealService#move} sets
   * it. Never the audit trail: it is append-only.
   */
  private void backdate(long w, DealResponse deal, Instant created, Instant now) {
    List<Long> changes =
        jdbc.queryForList(
            "SELECT id FROM deal_stage_changes WHERE workspace_id = ? AND deal_id = ? ORDER BY id",
            Long.class,
            w,
            deal.id());
    Duration step =
        Duration.between(created, now).dividedBy(changes.size()).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime start = created.atOffset(ZoneOffset.UTC);
    for (int k = 0; k < changes.size(); k++) {
      jdbc.update(
          "UPDATE deal_stage_changes SET changed_at = ? WHERE workspace_id = ? AND id = ?",
          start.plus(step.multipliedBy(k)),
          w,
          changes.get(k));
    }
    OffsetDateTime lastChange = start.plus(step.multipliedBy(changes.size() - 1));
    jdbc.update(
        "UPDATE deals SET created_at = ?, closed_at = ? WHERE workspace_id = ? AND id = ?",
        start,
        deal.closedAt() == null ? null : lastChange,
        w,
        deal.id());
  }

  private static long pick(List<Long> people, Random random) {
    return people.get(random.nextInt(people.size()));
  }

  private static BigDecimal amount(Random random) {
    return BigDecimal.valueOf(1_000 + 500L * random.nextInt(60));
  }

  private static String email(String first, String last) {
    return (first + "." + last).toLowerCase(Locale.ROOT) + "@example.com";
  }
}
