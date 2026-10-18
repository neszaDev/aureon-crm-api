package com.example.boilerplate.crm.insight.dto;

import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.workspace.dto.MemberRef;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** An owner's open deals (no owner: the unassigned ones) and their amounts per currency. */
public record OwnerBucket(MemberRef owner, long count, @NotNull List<MoneyTotal> totals) {}
