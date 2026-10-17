package com.example.boilerplate.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  List<AuditEvent> findByWorkspaceIdOrderByOccurredAtDescIdDesc(Long workspaceId);
}
