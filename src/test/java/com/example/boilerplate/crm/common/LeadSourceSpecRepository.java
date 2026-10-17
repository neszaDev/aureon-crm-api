package com.example.boilerplate.crm.common;

import com.example.boilerplate.crm.settings.LeadSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Test only: the way every B2 record repository lists, on a table that has a nullable column. */
public interface LeadSourceSpecRepository
    extends JpaRepository<LeadSource, Long>, JpaSpecificationExecutor<LeadSource> {}
