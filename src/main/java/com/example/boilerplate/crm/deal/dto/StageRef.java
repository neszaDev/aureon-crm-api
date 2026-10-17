package com.example.boilerplate.crm.deal.dto;

import com.example.boilerplate.crm.settings.StageKind;

/** The stage a deal is in. */
public record StageRef(long id, String name, StageKind kind) {}
