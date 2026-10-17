package com.example.boilerplate.crm.company.dto;

/**
 * The list's query parameters besides paging and sorting. {@code q}: name, website or phone contain
 * it (any case); {@code industry}: exactly, in any case; {@code archived}: only archived companies
 * (default: only active ones).
 */
public record CompanyFilter(String q, Long ownerId, String industry, boolean archived) {}
