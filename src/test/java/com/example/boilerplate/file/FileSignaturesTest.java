package com.example.boilerplate.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileSignaturesTest {
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

  @Test
  void acceptsKnownSignatures() {
    assertThat(FileSignatures.matches("image/png", PNG)).isTrue();
    assertThat(FileSignatures.matches("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isTrue();
    assertThat(FileSignatures.matches("image/gif", ascii("GIF89a..."))).isTrue();
    assertThat(FileSignatures.matches("image/webp", ascii("RIFF\0\0\0\0WEBPVP8 "))).isTrue();
    assertThat(FileSignatures.matches("application/pdf", ascii("%PDF-1.7\n"))).isTrue();
    assertThat(FileSignatures.matches("text/csv", ascii("a,b\n1,2\n"))).isTrue();
  }

  @Test
  void rejectsContentThatDoesNotFitTheType() {
    assertThat(FileSignatures.matches("image/png", ascii("<script>alert(1)</script>"))).isFalse();
    assertThat(FileSignatures.matches("image/png", new byte[] {(byte) 0x89, 'P'})).isFalse();
    assertThat(FileSignatures.matches("image/webp", ascii("RIFF\0\0\0\0WAVEfmt "))).isFalse();
    assertThat(FileSignatures.matches("application/pdf", PNG)).isFalse();
    assertThat(FileSignatures.matches("text/plain", PNG)).isFalse();
  }

  @Test
  void typesWithoutASignatureRelyOnTheAllowlist() {
    assertThat(FileSignatures.matches("application/zip", ascii("anything"))).isTrue();
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] bytes(int... values) {
    byte[] out = new byte[values.length];
    for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
    return out;
  }
}
