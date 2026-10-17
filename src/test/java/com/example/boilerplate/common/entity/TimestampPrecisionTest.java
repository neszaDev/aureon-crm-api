package com.example.boilerplate.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.deal.DealStageChange;
import com.example.boilerplate.crm.settings.LeadSource;
import com.example.boilerplate.crm.settings.PipelineStage;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Times the app sets are cut to microseconds, as PostgreSQL stores them, so a response equals a
 * later read on clocks with nanoseconds too (Linux). On a microsecond clock (macOS) this can't
 * fail.
 */
class TimestampPrecisionTest {
  static class Row extends AuditableEntity {}

  @Test
  void setTimesHaveMicrosecondsAtMost() {
    Row row = new Row();
    row.onCreate();
    assertMicros(row.getCreatedAt());
    assertMicros(row.getUpdatedAt());
    row.onUpdate();
    assertMicros(row.getUpdatedAt());

    LeadSource source = new LeadSource("Expo");
    source.update("Expo", true);
    assertMicros((Instant) ReflectionTestUtils.getField(source, "archivedAt"));

    assertMicros(new DealStageChange(1, null, new PipelineStage("New", 10), 1).getChangedAt());
  }

  private static void assertMicros(Instant time) {
    assertThat(time).isNotNull();
    assertThat(time.getNano() % 1000).isZero();
  }
}
