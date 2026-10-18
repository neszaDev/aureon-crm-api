package com.example.boilerplate.crm.insight.dto;

/**
 * Active companies and contacts, open leads (NEW, CONTACTED, QUALIFIED) and deals (in an open
 * stage), and the workspace's open tasks due in the past.
 */
public record Counts(
    long companies, long contacts, long openLeads, long openDeals, long overdueTasks) {}
