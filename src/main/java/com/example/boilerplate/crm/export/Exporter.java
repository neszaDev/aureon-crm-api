package com.example.boilerplate.crm.export;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.common.exception.BadRequestException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.crm.common.PersonRef;
import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceRepository;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the record exports share (api.md, Export): the {@code EXPORT} permission, the row cap, the
 * audit event and the file. A record service brings its list's specification, default order and
 * columns. MANDATORY: the service's transaction holds the tenant, and the event commits with the
 * read. Everything runs on the request thread, where the workspace context lives.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class Exporter {
  private final WorkspaceAccess access;
  private final WorkspaceRepository workspaces;
  private final AuditTrail audit;
  private final AuditChanges auditChanges;
  private final ExportProperties properties;
  private final Clock clock;

  public Exporter(
      WorkspaceAccess access,
      WorkspaceRepository workspaces,
      AuditTrail audit,
      AuditChanges auditChanges,
      ExportProperties properties,
      Clock clock) {
    this.access = access;
    this.workspaces = workspaces;
    this.audit = audit;
    this.auditChanges = auditChanges;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * The rows {@code matching} finds, in {@code order}, as {@code <list>-<today>.csv} (today in the
   * workspace's time zone). Over the cap: 400 {@code EXPORT_TOO_LARGE}, nothing read or recorded.
   * {@code filter} is the list's filter record, for the audit event.
   */
  public <T> CsvFile export(
      String list,
      Object filter,
      List<String> header,
      JpaSpecificationExecutor<T> repository,
      Specification<T> matching,
      Sort order,
      Function<List<T>, Stream<List<String>>> rows) {
    access.require(Permission.EXPORT);
    long count = repository.count(matching);
    if (count > properties.maxRows()) {
      throw new BadRequestException(
          ErrorCode.EXPORT_TOO_LARGE,
          "Too many rows to export (" + count + "); narrow the filters");
    }
    List<T> found = repository.findAll(matching, order);
    long workspaceId = WorkspaceContext.workspaceId();
    audit.record(
        AuditAction.EXPORT,
        AuditEntityType.WORKSPACE,
        workspaceId,
        found.size() + " " + list + " exported",
        changes(list, filter));
    StringWriter csv = new StringWriter();
    // Excel reads a CSV as UTF-8 only after a byte order mark.
    csv.write('\uFEFF');
    try {
      CsvWriter.write(csv, header, rows.apply(found));
    } catch (IOException e) {
      throw new UncheckedIOException(e); // a StringWriter doesn't throw it
    }
    String zone = workspaces.findById(workspaceId).orElseThrow().getTimezone();
    String filename = list + "-" + LocalDate.ofInstant(clock.instant(), ZoneId.of(zone)) + ".csv";
    return new CsvFile(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * One row of cells: null empty, booleans {@code yes}/{@code no}, amounts as stored, a member's
   * display name or else email, a reference's name, a list joined with {@code "; "}, the rest (ids,
   * ISO-8601 instants and dates, enums) as {@code toString}.
   */
  public static List<String> row(Object... values) {
    return Arrays.stream(values).map(Exporter::cell).toList();
  }

  private static String cell(Object value) {
    return switch (value) {
      case null -> null;
      case Boolean b -> b ? "yes" : "no";
      case BigDecimal amount -> amount.toPlainString();
      case MemberRef m -> m.displayName() != null ? m.displayName() : m.email();
      case Ref r -> r.name();
      case PersonRef p -> p.fullName();
      case List<?> l -> l.stream().map(String::valueOf).collect(Collectors.joining("; "));
      default -> value.toString();
    };
  }

  /**
   * The event's changes, shaped like an edit's: {@code list} and the {@code filters} given, without
   * the unset ones (null) and the flags left at their default (false).
   */
  private Map<String, Map<String, Object>> changes(String list, Object filter) {
    Map<String, Object> given = auditChanges.snapshot(filter);
    given.values().removeIf(Boolean.FALSE::equals);
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("list", list);
    after.put("filters", given);
    return AuditChanges.diff(Map.of(), after);
  }
}
