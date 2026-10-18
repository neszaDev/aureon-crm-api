package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.workspace.dto.MemberRef;

/**
 * Who moved a deal into a stage: the person of its newest history entry for that stage. {@code by}
 * is absent exactly when the history's {@code changedBy} is: that person is no longer a member.
 */
public record StageSignature(long stageId, MemberRef by) {}
