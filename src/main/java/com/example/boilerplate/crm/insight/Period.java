package com.example.boilerplate.crm.insight;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.crm.insight.dto.PeriodDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Whole days from {@code from} to {@code to}, both included, in the workspace's time zone: the
 * instants from {@link #start()} (inclusive) to {@link #endExclusive()}.
 */
public record Period(LocalDate from, LocalDate to, ZoneId zone) {
  static final int DEFAULT_DAYS = 30;
  static final int MAX_DAYS = 366;

  public PeriodDto dto() {
    return new PeriodDto(from, to);
  }

  public Instant start() {
    return from.atStartOfDay(zone).toInstant();
  }

  public Instant endExclusive() {
    return to.plusDays(1).atStartOfDay(zone).toInstant();
  }

  /**
   * The period a request asks for at {@code now}: {@code to} defaults to today in {@code zone},
   * {@code from} to 29 days before {@code to}. {@code from} after {@code to} is a 400 on {@code
   * from}, more than 366 days a 400 on {@code to}.
   */
  public static Period resolve(LocalDate from, LocalDate to, ZoneId zone, Instant now) {
    LocalDate end = to != null ? to : LocalDate.ofInstant(now, zone);
    LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
    if (start.isAfter(end)) throw new FieldValidationException("from", "must not be after to");
    if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
      throw new FieldValidationException("to", "must be at most 366 days from from");
    }
    return new Period(start, end, zone);
  }

  /**
   * The date query parameter {@code field} ({@code YYYY-MM-DD}), null when absent or blank, else a
   * 400 on that field. Years past 9999 are refused: the database can't hold their instants.
   */
  public static LocalDate date(String field, String value) {
    if (value == null || value.isBlank()) return null;
    LocalDate date;
    try {
      date = LocalDate.parse(value.strip());
    } catch (DateTimeParseException e) {
      date = null;
    }
    if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
      throw new FieldValidationException(field, "must be a date (YYYY-MM-DD)");
    }
    return date;
  }
}
