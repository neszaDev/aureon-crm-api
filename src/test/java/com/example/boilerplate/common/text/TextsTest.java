package com.example.boilerplate.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextsTest {
  // U+3000 ideographic space, U+00A0 no-break space: @NotBlank (String.trim) lets both through.
  static final List<String> BLANKS = List.of("", "   ", "\t\n", "　", " ", " 　  ");

  @Test
  void nextLineIsASpaceToo() {
    // U+0085 is neither Character.isWhitespace nor isSpaceChar, yet (?U)\s matches it.
    assertThat(Texts.optional("ada@example.com\u0085")).isEqualTo("ada@example.com");
    assertThat(Texts.optional("\u0085")).isNull();
  }

  @Test
  void requiredStripsEveryKindOfSpace() {
    assertThat(Texts.required("name", "  Acme \t")).isEqualTo("Acme");
    assertThat(Texts.required("name", "　Acme Corp ")).isEqualTo("Acme Corp");
    assertThat(Texts.required("name", "บริษัท ใจเย็น จำกัด")).isEqualTo("บริษัท ใจเย็น จำกัด");
  }

  @Test
  void requiredRejectsBlankValuesOnTheField() {
    for (String blank : BLANKS) {
      FieldValidationException e =
          assertThrows(FieldValidationException.class, () -> Texts.required("name", blank));
      assertThat(e.field()).isEqualTo("name");
      assertThat(e.getMessage()).isEqualTo("must not be blank");
    }
  }

  @Test
  void optionalIsNullWhenBlank() {
    assertThat(Texts.optional(null)).isNull();
    for (String blank : BLANKS) assertThat(Texts.optional(blank)).isNull();
    assertThat(Texts.optional(" 　สมชาย ใจดี ")).isEqualTo("สมชาย ใจดี");
  }
}
