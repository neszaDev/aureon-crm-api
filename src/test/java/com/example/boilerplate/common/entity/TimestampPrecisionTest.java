package com.example.boilerplate.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.deal.DealStageChange;
import com.example.boilerplate.crm.settings.LeadSource;
import com.example.boilerplate.crm.settings.PipelineStage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Times the app sets are cut to microseconds, as PostgreSQL stores them, so a response equals a
 * later read on clocks with nanoseconds too (Linux). On a microsecond clock (macOS, the CI runners)
 * the first test can't fail, so the second reads the entities' source.
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

  /** Every {@code Instant.now()} in an entity is cut to microseconds where it is read. */
  @Test
  void everyNowAnEntityTakesIsCutToMicroseconds() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      List<String> uncut =
          files
              .filter(f -> f.toString().endsWith(".java"))
              .flatMap(TimestampPrecisionTest::uncutNows)
              .toList();
      assertThat(uncut).as("Instant.now() without truncatedTo(MICROS) in entities").isEmpty();
    }
  }

  private static Stream<String> uncutNows(Path file) {
    try {
      List<String> lines = Files.readAllLines(file);
      if (lines.stream()
          .noneMatch(l -> l.startsWith("@Entity") || l.startsWith("@MappedSuperclass"))) {
        return Stream.empty();
      }
      return lines.stream()
          .filter(l -> l.contains("Instant.now()"))
          .filter(l -> !l.contains("Instant.now().truncatedTo(ChronoUnit.MICROS)"))
          .map(l -> file.getFileName() + ": " + l.strip());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void assertMicros(Instant time) {
    assertThat(time).isNotNull();
    assertThat(time.getNano() % 1000).isZero();
  }
}
