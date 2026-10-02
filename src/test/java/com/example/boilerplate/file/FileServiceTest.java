package com.example.boilerplate.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileServiceTest {
  @Test
  void safeNameDropsPathsAndControlCharacters() {
    assertThat(FileService.safeName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(FileService.safeName("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
    assertThat(FileService.safeName("bad\r\nname\u0000.txt")).isEqualTo("badname.txt");
    assertThat(FileService.safeName("  spaced.txt  ")).isEqualTo("spaced.txt");
  }

  @Test
  void safeNameFallsBackAndCapsLength() {
    assertThat(FileService.safeName(null)).isEqualTo("file");
    assertThat(FileService.safeName("dir/")).isEqualTo("file");
    assertThat(FileService.safeName("..")).isEqualTo("file");
    assertThat(FileService.safeName("a".repeat(300) + ".txt")).hasSize(255).endsWith(".txt");
  }

  @Test
  void normalizeTypeLowercasesAndDropsParameters() {
    assertThat(FileService.normalizeType("Text/Plain; charset=UTF-8")).isEqualTo("text/plain");
    assertThat(FileService.normalizeType(null)).isEqualTo("application/octet-stream");
    assertThat(FileService.normalizeType(" ")).isEqualTo("application/octet-stream");
  }
}
