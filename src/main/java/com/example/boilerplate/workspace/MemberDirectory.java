package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.MemberRef;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Owner, assignee and author names for responses: the members of the current workspace among the
 * given user ids. {@code WorkspaceMember} is not tenant-filtered, so the workspace is bound here.
 */
@Component
public class MemberDirectory {
  private final WorkspaceMemberRepository members;

  public MemberDirectory(WorkspaceMemberRepository members) {
    this.members = members;
  }

  /** One query for any number of ids; null ids and ids of non-members are left out. */
  public Map<Long, MemberRef> refs(Collection<Long> userIds) {
    List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) return Map.of();
    return members.findRefs(WorkspaceContext.workspaceId(), ids).stream()
        .collect(Collectors.toMap(MemberRef::userId, Function.identity()));
  }

  public Optional<MemberRef> ref(Long userId) {
    if (userId == null) return Optional.empty();
    return Optional.ofNullable(refs(List.of(userId)).get(userId));
  }
}
