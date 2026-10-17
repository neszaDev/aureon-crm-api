package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.boilerplate.common.exception.FieldValidationException;
import org.junit.jupiter.api.Test;

class TextSearchTest {
  @Test
  void trimsLowerCasesAndEscapesLikeWildcards() {
    assertThat(TextSearch.pattern(" 50%_off ")).contains("%50\\%\\_off%");
    assertThat(TextSearch.pattern("ACME Ltd")).contains("%acme ltd%");
  }

  @Test
  void aBackslashIsDoubled() {
    assertThat(TextSearch.pattern("a\\b")).contains("%a\\\\b%");
  }

  @Test
  void blankMeansNoSearch() {
    assertThat(TextSearch.pattern(null)).isEmpty();
    assertThat(TextSearch.pattern("")).isEmpty();
    // U+3000 and U+00A0 are blank too, as in Texts.
    assertThat(TextSearch.pattern(" 　  ")).isEmpty();
  }

  @Test
  void aLongTermIsRejectedOnTheQueryField() {
    assertThat(TextSearch.pattern("a".repeat(200))).isPresent();
    assertThatThrownBy(() -> TextSearch.pattern("a".repeat(201)))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> {
              assertThat(e.field()).isEqualTo("q");
              assertThat(e).hasMessage("is too long");
            });
  }
}
