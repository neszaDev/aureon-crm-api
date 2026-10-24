package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.user.User;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** WorkspaceMember is not tenant-filtered: the directory must scope itself to the workspace. */
class MemberDirectoryIT extends WorkspaceTestSupport {
  @Autowired MemberDirectory directory;

  @Test
  void resolvesOnlyMembersOfTheCurrentWorkspace() throws Exception {
    String emailA = uniqueEmail();
    String emailB = uniqueEmail();
    String emailC = uniqueEmail();
    long wa = createWorkspace(tokenFor(emailA), "A");
    long wb = createWorkspace(tokenFor(emailB), "B");
    tokenFor(emailC);
    addMember(wa, emailC, WorkspaceRole.SALES_REP);
    User c = users.findByEmail(emailC).orElseThrow();
    c.changeDisplayName("Cee");
    users.save(c);
    Membership inA = membership(wa, emailA);
    Membership inB = membership(wb, emailB);
    long idA = inA.userId();
    long idB = inB.userId();
    long idC = c.getId();
    List<Long> everyone = List.of(idA, idB, idC);

    Map<Long, MemberRef> seenFromA = WorkspaceContext.runAs(inA, () -> directory.refs(everyone));
    Map<Long, MemberRef> seenFromB = WorkspaceContext.runAs(inB, () -> directory.refs(everyone));

    assertThat(seenFromA).containsOnlyKeys(idA, idC);
    assertThat(seenFromA.get(idA)).isEqualTo(new MemberRef(idA, emailA, null));
    assertThat(seenFromA.get(idC)).isEqualTo(new MemberRef(idC, emailC, "Cee"));
    assertThat(seenFromB).containsOnlyKeys(idB);
  }

  @Test
  void refIsEmptyForNonMembersAndNull() throws Exception {
    String emailA = uniqueEmail();
    String emailB = uniqueEmail();
    long wa = createWorkspace(tokenFor(emailA), "A");
    long wb = createWorkspace(tokenFor(emailB), "B");
    Membership inA = membership(wa, emailA);
    long idB = membership(wb, emailB).userId();

    assertThat(WorkspaceContext.runAs(inA, () -> directory.ref(inA.userId())))
        .contains(new MemberRef(inA.userId(), emailA, null));
    assertThat(WorkspaceContext.runAs(inA, () -> directory.ref(idB))).isEmpty();
    assertThat(WorkspaceContext.runAs(inA, () -> directory.ref(null))).isEqualTo(Optional.empty());
    assertThat(WorkspaceContext.runAs(inA, () -> directory.ref(Long.MAX_VALUE))).isEmpty();
  }

  @Test
  void emptyInputAndNullIdsAreIgnored() throws Exception {
    String emailA = uniqueEmail();
    long wa = createWorkspace(tokenFor(emailA), "A");
    Membership inA = membership(wa, emailA);

    assertThat(WorkspaceContext.runAs(inA, () -> directory.refs(List.of()))).isEmpty();
    assertThat(WorkspaceContext.runAs(inA, () -> directory.refs(Arrays.asList(null, inA.userId()))))
        .containsOnlyKeys(inA.userId());
  }
}
