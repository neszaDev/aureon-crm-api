package com.example.boilerplate.crm.export;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** An export, ready to download: its file name and its UTF-8 bytes, BOM first. */
public record CsvFile(String filename, byte[] content) {
  private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

  public ResponseEntity<byte[]> response() {
    return ResponseEntity.ok()
        .contentType(CSV)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(content);
  }
}
