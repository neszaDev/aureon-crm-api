package com.example.boilerplate.crm.common;

/** A record referenced by another (company, stage, source): enough to show a link. */
public record Ref(long id, String name) {}
