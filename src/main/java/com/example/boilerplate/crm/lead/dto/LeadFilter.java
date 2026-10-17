package com.example.boilerplate.crm.lead.dto;

import com.example.boilerplate.crm.common.Priority;
import com.example.boilerplate.crm.lead.LeadStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * The list's query parameters besides paging and sorting. {@code q}: first or last name, both
 * together, company name, email or phone contain it (any case); {@code statuses}: any of them (null
 * or empty: all); {@code followUpBefore}: follow-up date on or before it, so leads without one
 * never match; {@code archived}: only archived leads (default: only active ones).
 */
public record LeadFilter(
    String q,
    List<LeadStatus> statuses,
    Long ownerId,
    Long sourceId,
    Priority priority,
    LocalDate followUpBefore,
    boolean archived) {}
