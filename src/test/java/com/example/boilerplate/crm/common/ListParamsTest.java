package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.boilerplate.common.exception.FieldValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.NullHandling;

class ListParamsTest {
  enum Field implements SortField {
    CREATED_AT(Direction.DESC, Key.of("createdAt")),
    NAME(Direction.ASC, Key.ignoringCase("name")),
    FULL_NAME(Direction.ASC, Key.ignoringCase("lastName"), Key.of("firstName")),
    COMPANY(Direction.ASC, Key.ignoringCase("company.name"));

    private final Direction defaultDirection;
    private final List<Key> keys;

    Field(Direction defaultDirection, Key... keys) {
      this.defaultDirection = defaultDirection;
      this.keys = List.of(keys);
    }

    @Override
    public Direction defaultDirection() {
      return defaultDirection;
    }

    @Override
    public List<Key> keys() {
      return keys;
    }
  }

  @Test
  void sortsByThePropertyThenIdInTheSameDirection() {
    Pageable p = ListParams.pageable(0, 20, Direction.ASC, "name");

    assertThat(p.getPageNumber()).isZero();
    assertThat(p.getPageSize()).isEqualTo(20);
    assertThat(p.getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(tuple("name", Direction.ASC), tuple("id", Direction.ASC));
  }

  @Test
  void descendingAppliesToTheTieBreakerToo() {
    Pageable p = ListParams.pageable(2, 50, Direction.DESC, "createdAt");

    assertThat(p.getPageNumber()).isEqualTo(2);
    assertThat(p.getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(tuple("createdAt", Direction.DESC), tuple("id", Direction.DESC));
  }

  @Test
  void aRequestWithoutADirectionTakesTheSortsOwn() {
    assertThat(ListParams.direction(null, Field.CREATED_AT)).isEqualTo(Direction.DESC);
    assertThat(ListParams.direction(null, Field.NAME)).isEqualTo(Direction.ASC);
    assertThat(ListParams.direction(Direction.ASC, Field.CREATED_AT)).isEqualTo(Direction.ASC);
    assertThat(ListParams.direction(Direction.DESC, Field.NAME)).isEqualTo(Direction.DESC);
  }

  @Test
  void aSortFieldGivesItsProperty() {
    assertThat(ListParams.pageable(0, 20, Direction.DESC, Field.CREATED_AT).getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(tuple("createdAt", Direction.DESC), tuple("id", Direction.DESC));
  }

  @Test
  void aSortFieldThatIgnoresCaseLeavesTheIdTieBreakerAlone() {
    assertThat(ListParams.pageable(0, 20, Direction.DESC, Field.NAME).getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection, Sort.Order::isIgnoreCase)
        .containsExactly(tuple("name", Direction.DESC, true), tuple("id", Direction.DESC, false));
    assertThat(ListParams.pageable(0, 20, Direction.ASC, Field.CREATED_AT).getSort().toList())
        .extracting(Sort.Order::isIgnoreCase)
        .containsOnly(false);
  }

  @Test
  void aSortFieldOfSeveralKeysKeepsTheirOrderAndCaseThenTheId() {
    assertThat(ListParams.pageable(0, 20, Direction.DESC, Field.FULL_NAME).getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection, Sort.Order::isIgnoreCase)
        .containsExactly(
            tuple("lastName", Direction.DESC, true),
            tuple("firstName", Direction.DESC, false),
            tuple("id", Direction.DESC, false));
    // A path through a to-one link: Spring Data left-joins it for the sort.
    assertThat(ListParams.pageable(0, 20, Direction.ASC, Field.COMPANY).getSort().toList())
        .extracting(Sort.Order::getProperty, Sort.Order::isIgnoreCase)
        .containsExactly(tuple("company.name", true), tuple("id", false));
  }

  @Test
  void nullHandlingStaysNativeBecauseCriteriaSortsCannotChangeIt() {
    // Spring Data's Criteria sorting throws for anything else: see Ordering.nullsLast.
    for (Direction d : Direction.values()) {
      assertThat(ListParams.pageable(0, 20, d, "dueAt").getSort().toList())
          .allSatisfy(o -> assertThat(o.getNullHandling()).isEqualTo(NullHandling.NATIVE));
    }
  }

  @Test
  void sortingByIdAddsNoSecondOrder() {
    assertThat(ListParams.pageable(0, 20, Direction.ASC, "id").getSort().toList())
        .extracting(Sort.Order::getProperty)
        .containsExactly("id");
  }

  @Test
  void unsortedKeepsOnlyThePage() {
    Pageable p = ListParams.unsorted(3, 10);

    assertThat(p.getPageNumber()).isEqualTo(3);
    assertThat(p.getPageSize()).isEqualTo(10);
    assertThat(p.getSort().isSorted()).isFalse();
  }

  @Test
  void pageAndSizeAreCheckedOnTheirOwnFields() {
    assertThat(ListParams.pageable(0, 1, Direction.ASC, "name")).isNotNull();
    assertThat(ListParams.pageable(0, 100, Direction.ASC, "name")).isNotNull();
    assertInvalid("page", () -> ListParams.pageable(-1, 20, Direction.ASC, "name"));
    assertInvalid("size", () -> ListParams.pageable(0, 0, Direction.ASC, "name"));
    assertInvalid("size", () -> ListParams.pageable(0, 101, Direction.ASC, "name"));
    assertInvalid("page", () -> ListParams.unsorted(-1, 20));
    assertInvalid("size", () -> ListParams.unsorted(0, 101));
    // The offset (page * size) must fit an int: the database query takes it as one.
    assertThat(ListParams.unsorted(Integer.MAX_VALUE / 100, 100)).isNotNull();
    assertInvalid("page", () -> ListParams.unsorted(Integer.MAX_VALUE / 100 + 1, 100));
    assertInvalid("page", () -> ListParams.pageable(Integer.MAX_VALUE, 20, Direction.ASC, "name"));
  }

  private static void assertInvalid(String field, Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            FieldValidationException.class, e -> assertThat(e.field()).isEqualTo(field));
  }
}
