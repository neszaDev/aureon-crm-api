package com.example.boilerplate.crm.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Scoped to the current workspace by {@code @TenantId}, including {@code findById}. */
public interface ActivityRepository
    extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {}
