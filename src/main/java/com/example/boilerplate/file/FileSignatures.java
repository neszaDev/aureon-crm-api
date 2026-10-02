package com.example.boilerplate.file;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks that a file's first bytes fit its declared type, so a script can't be uploaded as {@code
 * image/png}. Types without a known signature rely on the allowlist alone.
 */
final class FileSignatures {
  /** How many leading bytes {@link #matches} needs to see. */
  static final int HEAD_BYTES = 8192;

  private FileSignatures() {}

  static boolean matches(String contentType, byte[] head) {
    return switch (contentType) {
      case "image/png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
      case "image/jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
      case "image/gif" ->
          startsWith(head, 0, ascii("GIF87a")) || startsWith(head, 0, ascii("GIF89a"));
      case "image/webp" -> startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WEBP"));
      case "application/pdf" -> startsWith(head, 0, ascii("%PDF-"));
      default -> !contentType.startsWith("text/") || isText(head);
    };
  }

  private static boolean isText(byte[] head) {
    for (byte b : head) if (b == 0) return false;
    return true;
  }

  private static boolean startsWith(byte[] head, int offset, int... expected) {
    if (head.length < offset + expected.length) return false;
    for (int i = 0; i < expected.length; i++)
      if ((head[offset + i] & 0xFF) != expected[i]) return false;
    return true;
  }

  private static int[] ascii(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
    int[] values = new int[bytes.length];
    Arrays.setAll(values, i -> bytes[i]);
    return values;
  }
}
