package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.crm.common.Ref;
import com.example.boilerplate.workspace.dto.MemberRef;
import java.time.Instant;

/**
 * An entry of a deal's stage history. {@code fromStage} is null for the first one; {@code
 * changedBy} once that person is no longer a member.
 */
public record StageChangeResponse(
    Ref fromStage, Ref toStage, MemberRef changedBy, Instant changedAt) {}
