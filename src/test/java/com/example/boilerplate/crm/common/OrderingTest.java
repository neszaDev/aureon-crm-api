package com.example.boilerplate.crm.common;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

class OrderingTest {
  @Test
  @SuppressWarnings("unchecked")
  void aCountQueryIsLeftAlone() {
    for (Class<?> type : new Class<?>[] {Long.class, long.class}) {
      CriteriaQuery<Object> query = mock(CriteriaQuery.class);
      when(query.getResultType()).thenAnswer(i -> type);
      Root<Object> root = mock(Root.class);
      CriteriaBuilder cb = mock(CriteriaBuilder.class);

      Ordering.<Object>nullsLast("dueAt", Direction.ASC).toPredicate(root, query, cb);
      Ordering.<Object>byPriority("priority", Direction.DESC).toPredicate(root, query, cb);

      verify(query, times(2)).getResultType();
      verifyNoMoreInteractions(query, root, cb);
    }
  }
}
