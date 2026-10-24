package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Security review probe (phase 4): a sign-in with an unknown email costs a full BCrypt check, as a
 * wrong password does. That holds only if the dummy hash is one BCrypt accepts at the encoder's
 * cost: a malformed one makes {@code matches} return false at once, and the timing tells which
 * emails have accounts.
 */
class DummyHashTest {
  @Test
  void theDummyHashIsAWellFormedBcryptHashAtTheEncodersCost() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    String real = encoder.encode("any-password");

    // The encoder's own format check (it warns and answers false without hashing otherwise).
    assertThat(AuthService.DUMMY_HASH).matches("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");
    // The same cost as real hashes: the same work.
    assertThat(AuthService.DUMMY_HASH.substring(4, 6)).isEqualTo(real.substring(4, 6));
    // BCrypt computes it in full (a bad salt would throw here) and nothing matches it.
    assertThat(BCrypt.checkpw("any-password", AuthService.DUMMY_HASH)).isFalse();
    assertThat(encoder.matches("", AuthService.DUMMY_HASH)).isFalse();
  }
}
