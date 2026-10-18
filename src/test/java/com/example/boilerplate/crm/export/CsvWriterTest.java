package com.example.boilerplate.crm.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CsvWriterTest {
  @Test
  void writesTheHeaderThenEachRowWithCrlf() throws IOException {
    StringWriter out = new StringWriter();
    CsvWriter.write(
        out,
        List.of("Reference", "Name"),
        Stream.of(List.of("C-0001", "Acme"), List.of("C-0002", "Globex")));
    assertThat(out).hasToString("Reference,Name\r\nC-0001,Acme\r\nC-0002,Globex\r\n");
  }

  @Test
  void aPlainValueStaysAsItIs() throws IOException {
    assertThat(cell("Acme Ltd")).isEqualTo("Acme Ltd");
  }

  @Test
  void aCommaQuotes() throws IOException {
    assertThat(cell("Acme, Inc.")).isEqualTo("\"Acme, Inc.\"");
  }

  @Test
  void aQuoteIsDoubledInsideQuotes() throws IOException {
    assertThat(cell("He said \"hi\"")).isEqualTo("\"He said \"\"hi\"\"\"");
  }

  @Test
  void lineBreaksQuote() throws IOException {
    assertThat(cell("line 1\nline 2")).isEqualTo("\"line 1\nline 2\"");
    assertThat(cell("line 1\r\nline 2")).isEqualTo("\"line 1\r\nline 2\"");
  }

  @Test
  void aFormulaGetsAnApostropheFirst() throws IOException {
    assertThat(cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    assertThat(cell("-5")).isEqualTo("'-5");
    assertThat(cell("@x")).isEqualTo("'@x");
    assertThat(cell("+66 81 234 5678")).isEqualTo("'+66 81 234 5678");
    assertThat(cell("\tcmd")).isEqualTo("'\tcmd");
    // Quoting is decided after the apostrophe: a carriage return still quotes.
    assertThat(cell("\rcmd")).isEqualTo("\"'\rcmd\"");
    assertThat(cell("=HYPERLINK(\"http://x\",\"y\")"))
        .isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
    // Only the first character counts.
    assertThat(cell("a=b-c")).isEqualTo("a=b-c");
  }

  /** Spreadsheets skip leading blanks (also no-break and ideographic spaces) before a formula. */
  @Test
  void aFormulaAfterLeadingWhitespaceGetsAnApostropheFirst() throws IOException {
    assertThat(cell(" =SUM(A1)")).isEqualTo("' =SUM(A1)");
    assertThat(cell("  +66 81 234 5678")).isEqualTo("'  +66 81 234 5678");
    assertThat(cell("\u00A0-5")).isEqualTo("'\u00A0-5");
    assertThat(cell("\u3000@x")).isEqualTo("'\u3000@x");
    // The apostrophe goes first; the line break then quotes.
    assertThat(cell(" \n=1+1")).isEqualTo("\"' \n=1+1\"");
    // Only blanks, or blanks before other text, are no formula.
    assertThat(cell("   ")).isEqualTo("   ");
    assertThat(cell(" a=b")).isEqualTo(" a=b");
    assertThat(cell("\u00A0Acme")).isEqualTo("\u00A0Acme");
  }

  /** Semicolon-locale Excel splits on {@code ;} but honours quotes. */
  @Test
  void aSemicolonQuotes() throws IOException {
    assertThat(cell("bangkok; vip")).isEqualTo("\"bangkok; vip\"");
    assertThat(cell("x;=HYPERLINK(\"http://x\",\"y\")"))
        .isEqualTo("\"x;=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
  }

  @Test
  void nullAndEmptyAreEmpty() throws IOException {
    StringWriter out = new StringWriter();
    CsvWriter.write(out, List.of("a", "b", "c"), Stream.of(Arrays.asList(null, "", "x")));
    assertThat(out).hasToString("a,b,c\r\n,,x\r\n");
  }

  @Test
  void thaiTextIsUnchanged() throws IOException {
    assertThat(cell("สมชาย ใจดี")).isEqualTo("สมชาย ใจดี");
  }

  @Test
  void theHeaderIsEscapedLikeAnyRow() throws IOException {
    StringWriter out = new StringWriter();
    CsvWriter.write(out, List.of("Open deals", "a,b"), Stream.empty());
    assertThat(out).hasToString("Open deals,\"a,b\"\r\n");
  }

  /** {@code value} as the only cell of a row, as written. */
  private static String cell(String value) throws IOException {
    StringWriter out = new StringWriter();
    CsvWriter.write(out, List.of("h"), Stream.of(Arrays.asList(value)));
    String csv = out.toString();
    assertThat(csv).startsWith("h\r\n").endsWith("\r\n");
    return csv.substring(3, csv.length() - 2);
  }
}
