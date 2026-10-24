package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.settings.LeadSource;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;

/** The shared list helpers against PostgreSQL, through {@code findAll(spec, pageable)}. */
class ListHelpersIT extends WorkspaceTestSupport {
  /** The rows a test saved, not the default sources every workspace starts with. */
  private static final Specification<LeadSource> MINE =
      (root, query, cb) -> cb.like(root.<String>get("name"), "z-%");

  @Autowired LeadSourceSpecRepository sources;

  private Membership newWorkspace() throws Exception {
    String email = uniqueEmail();
    long id = createWorkspace(tokenFor(email), "Lists");
    return membership(id, email);
  }

  private void save(Membership m, String name, boolean archived) throws Exception {
    LeadSource source = new LeadSource(name);
    if (archived) source.update(name, true);
    WorkspaceContext.runAs(m, () -> sources.save(source));
    Thread.sleep(5); // distinct archive times
  }

  private Page<String> names(Membership m, Specification<LeadSource> spec, Pageable pageable) {
    return WorkspaceContext.runAs(
        m, () -> sources.findAll(spec, pageable).map(LeadSource::getName));
  }

  /** What a list endpoint does with {@code q}: Postgres lowers both sides. */
  private List<String> search(Membership m, String q) {
    String pattern = TextSearch.pattern(q).orElseThrow();
    Specification<LeadSource> spec =
        (root, query, cb) ->
            cb.like(
                cb.lower(root.<String>get("name")), cb.lower(TextSearch.bound(cb, pattern)), '\\');
    return names(m, spec, ListParams.pageable(0, 100, Direction.ASC, "name")).getContent();
  }

  @Test
  void searchMatchesWildcardsAndBackslashesLiterally() throws Exception {
    Membership m = newWorkspace();
    for (String name :
        new String[] {
          "50%_off deal", "50 percent off", "a\\b", "ab", "x_y", "xzy", "Mixed CASE", "plain"
        }) {
      save(m, name, false);
    }

    // Unescaped, "50%_off" would also match "50 percent off", "a\b" would match "ab".
    assertThat(search(m, " 50%_off ")).containsExactly("50%_off deal");
    assertThat(search(m, "a\\b")).containsExactly("a\\b");
    assertThat(search(m, "%")).containsExactly("50%_off deal");
    assertThat(search(m, "_")).containsExactly("50%_off deal", "x_y");
    assertThat(search(m, "mixed case")).containsExactly("Mixed CASE");
    assertThat(search(m, "MIXED")).containsExactly("Mixed CASE");
    assertThat(search(m, "plain")).containsExactly("plain");
  }

  @Test
  void listParamsSortWithTheIdTieBreakerInBothDirections() throws Exception {
    Membership m = newWorkspace();
    for (String name : new String[] {"z-b", "z-a", "z-c"}) save(m, name, false);

    // Every row has version 0, so only the id tie-breaker orders them: saved order, then reversed.
    assertThat(names(m, MINE, ListParams.pageable(0, 20, Direction.ASC, "version")).getContent())
        .containsExactly("z-b", "z-a", "z-c");
    assertThat(names(m, MINE, ListParams.pageable(0, 20, Direction.DESC, "version")).getContent())
        .containsExactly("z-c", "z-a", "z-b");

    assertThat(names(m, MINE, ListParams.pageable(0, 20, Direction.ASC, "name")).getContent())
        .containsExactly("z-a", "z-b", "z-c");
    Page<String> firstPage = names(m, MINE, ListParams.pageable(0, 2, Direction.DESC, "name"));
    assertThat(firstPage.getContent()).containsExactly("z-c", "z-b");
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(names(m, MINE, ListParams.pageable(1, 2, Direction.DESC, "name")).getContent())
        .containsExactly("z-a");
  }

  @Test
  void nullsLastOrderingWorksAscendingAndDescendingAndLeavesTheCountAlone() throws Exception {
    Membership m = newWorkspace();
    save(m, "z-null-1", false);
    save(m, "z-early", true);
    save(m, "z-null-2", false);
    save(m, "z-late", true);

    Specification<LeadSource> asc = MINE.and(Ordering.nullsLast("archivedAt", Direction.ASC));
    Specification<LeadSource> desc = MINE.and(Ordering.nullsLast("archivedAt", Direction.DESC));

    // Nulls are last in both directions, and the id tie-breaker follows the direction.
    Page<String> page0 = names(m, asc, ListParams.unsorted(0, 2));
    assertThat(page0.getContent()).containsExactly("z-early", "z-late");
    assertThat(page0.getTotalElements()).isEqualTo(4);
    assertThat(names(m, asc, ListParams.unsorted(1, 2)).getContent())
        .containsExactly("z-null-1", "z-null-2");

    Page<String> descPage0 = names(m, desc, ListParams.unsorted(0, 2));
    assertThat(descPage0.getContent()).containsExactly("z-late", "z-early");
    assertThat(descPage0.getTotalElements()).isEqualTo(4);
    assertThat(names(m, desc, ListParams.unsorted(1, 2)).getContent())
        .containsExactly("z-null-2", "z-null-1");
  }
}
