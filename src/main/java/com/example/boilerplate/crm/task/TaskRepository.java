package com.example.boilerplate.crm.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface TaskRepository
    extends JpaRepository<CrmTask, Long>, JpaSpecificationExecutor<CrmTask> {}
