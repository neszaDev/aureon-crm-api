package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Hibernate writes a {@code cb.literal(text)} into the SQL text, so request text in one is an
 * injection risk and a NUL in it kills the connection. Use {@code TextSearch.bound} (or a named
 * parameter). A source scan: the behaviour is pinned by {@code BoundTextFilterIT}, this catches a
 * filter that test does not reach.
 */
class NoInlineLiteralTest {
  @Test
  void noCriteriaLiteralInMain() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      List<String> found =
          files
              .filter(f -> f.toString().endsWith(".java"))
              .filter(NoInlineLiteralTest::hasLiteral)
              .map(Path::toString)
              .toList();
      assertThat(found).as("files calling literal(...)").isEmpty();
    }
  }

  private static boolean hasLiteral(Path file) {
    try {
      // Javadoc may name it; a call is ".literal(" outside comments.
      return Files.readAllLines(file).stream()
          .map(String::strip)
          .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
          .anyMatch(l -> l.contains(".literal("));
    } catch (IOException e) {
      throw new IllegalStateException(file.toString(), e);
    }
  }
}
