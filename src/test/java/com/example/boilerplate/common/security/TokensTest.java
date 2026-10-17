package com.example.boilerplate.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokensTest {
  @Test
  void randomIsAUrlSafeUnpaddedEncodingOf32Bytes() {
    String token = Tokens.random();
    assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    assertThat(Tokens.random()).isNotEqualTo(token);
  }

  @Test
  void sha256HexMatchesTheKnownDigest() {
    assertThat(Tokens.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
