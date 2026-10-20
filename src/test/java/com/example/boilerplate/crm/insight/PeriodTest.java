package com.example.boilerplate.crm.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PeriodTest {
  private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

  @Test
  void defaultsToTheLast30DaysInTheZone() {
    Period p = Period.resolve(null, null, BANGKOK, NOW);

    assertThat(p.from()).isEqualTo(LocalDate.parse("2026-09-18"));
    assertThat(p.to()).isEqualTo(LocalDate.parse("2026-10-17"));
    assertThat(p.zone()).isEqualTo(BANGKOK);
    // 18:00 UTC is already the 18th in Bangkok (UTC+7): today is the zone's.
    Instant evening = Instant.parse("2026-10-17T18:00:00Z");
    assertThat(Period.resolve(null, null, BANGKOK, evening).to())
        .isEqualTo(LocalDate.parse("2026-10-18"));
  }

  @Test
  void oneBoundGivenTheOtherDefaults() {
    LocalDate to = LocalDate.parse("2026-03-01");
    assertThat(Period.resolve(null, to, BANGKOK, NOW).from())
        .isEqualTo(LocalDate.parse("2026-01-31"));
    LocalDate from = LocalDate.parse("2026-10-01");
    Period p = Period.resolve(from, null, BANGKOK, NOW);
    assertThat(p.from()).isEqualTo(from);
    assertThat(p.to()).isEqualTo(LocalDate.parse("2026-10-17"));
  }

  @Test
  void instantsAreWholeDaysInTheZone() {
    Period p = new Period(LocalDate.parse("2026-09-18"), LocalDate.parse("2026-10-17"), BANGKOK);

    assertThat(p.start()).isEqualTo(Instant.parse("2026-09-17T17:00:00Z"));
    assertThat(p.endExclusive()).isEqualTo(Instant.parse("2026-10-17T17:00:00Z"));
  }

  @Test
  void fromAfterToIsRejectedOnFrom() {
    assertThatThrownBy(
            () ->
                Period.resolve(
                    LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-17"), BANGKOK, NOW))
        .isInstanceOfSatisfying(
            FieldValidationException.class, e -> assertThat(e.field()).isEqualTo("from"));
    // Only from given, after today.
    assertThatThrownBy(() -> Period.resolve(LocalDate.parse("2026-10-18"), null, BANGKOK, NOW))
        .isInstanceOfSatisfying(
            FieldValidationException.class, e -> assertThat(e.field()).isEqualTo("from"));
  }

  @Test
  void atMost366DaysRejectedOnTo() {
    LocalDate to = LocalDate.parse("2026-10-17");
    assertThat(Period.resolve(to.minusDays(365), to, BANGKOK, NOW).from())
        .isEqualTo(to.minusDays(365));
    assertThat(Period.resolve(to, to, BANGKOK, NOW).from()).isEqualTo(to);

    // 367 days, both ends included.
    assertThatThrownBy(() -> Period.resolve(to.minusDays(366), to, BANGKOK, NOW))
        .isInstanceOfSatisfying(
            FieldValidationException.class, e -> assertThat(e.field()).isEqualTo("to"));
  }

  @Test
  void datesParseOrFailOnTheirField() {
    assertThat(Period.date("from", "2026-02-28")).isEqualTo(LocalDate.parse("2026-02-28"));
    assertThat(Period.date("from", null)).isNull();
    assertThat(Period.date("from", " ")).isNull();
    for (String bad : new String[] {"2026-02-30", "17/10/2026", "yesterday", "+10000-01-01"}) {
      assertThatThrownBy(() -> Period.date("to", bad))
          .as(bad)
          .isInstanceOfSatisfying(
              FieldValidationException.class, e -> assertThat(e.field()).isEqualTo("to"));
    }
  }
}
