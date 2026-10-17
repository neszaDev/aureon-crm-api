package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.NullHandling;

class ListParamsTest {
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
  void nullsComeLastInBothDirections() {
    for (Direction d : Direction.values()) {
      Sort.Order first = ListParams.pageable(0, 20, d, "dueAt").getSort().toList().get(0);
      assertThat(first.getNullHandling()).isEqualTo(NullHandling.NULLS_LAST);
    }
  }

  @Test
  void sortingByIdAddsNoSecondOrder() {
    assertThat(ListParams.pageable(0, 20, Direction.ASC, "id").getSort().toList())
        .extracting(Sort.Order::getProperty)
        .containsExactly("id");
  }
}
