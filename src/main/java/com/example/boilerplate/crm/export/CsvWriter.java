package com.example.boilerplate.crm.export;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * RFC 4180 CSV with CRLF line ends. A value starting with {@code = + - @}, a tab or a carriage
 * return gets a leading {@code '} so spreadsheets show it instead of running it as a formula; a
 * value with a quote, comma or line break is then quoted, its quotes doubled. Null is empty.
 */
public final class CsvWriter {
  private static final String FORMULA_STARTS = "=+-@\t\r";

  private CsvWriter() {}

  public static void write(Writer out, List<String> header, Stream<List<String>> rows)
      throws IOException {
    writeRow(out, header);
    for (Iterator<List<String>> it = rows.iterator(); it.hasNext(); ) writeRow(out, it.next());
  }

  private static void writeRow(Writer out, List<String> row) throws IOException {
    for (int i = 0; i < row.size(); i++) {
      if (i > 0) out.write(',');
      out.write(field(row.get(i)));
    }
    out.write("\r\n");
  }

  private static String field(String value) {
    if (value == null || value.isEmpty()) return "";
    String v = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    boolean quote =
        v.indexOf('"') >= 0 || v.indexOf(',') >= 0 || v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0;
    return quote ? '"' + v.replace("\"", "\"\"") + '"' : v;
  }
}
