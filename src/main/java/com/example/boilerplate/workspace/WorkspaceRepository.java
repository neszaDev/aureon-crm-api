package com.example.boilerplate.workspace;

import com.example.boilerplate.workspace.dto.WorkspaceSummaryResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
  @Query(
      """
      select new com.example.boilerplate.workspace.dto.WorkspaceSummaryResponse(w.id, w.name, m.role)
      from WorkspaceMember m
      join Workspace w on w.id = m.workspaceId
      where m.userId = :userId
      order by lower(w.name), w.id""")
  List<WorkspaceSummaryResponse> findSummariesByMemberId(long userId);
}
