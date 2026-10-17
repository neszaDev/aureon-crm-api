package com.example.boilerplate.crm.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.common.Priority;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskTest {
  // Rows: from, columns: to, both in declaration order (OPEN, COMPLETED, CANCELLED).
  private static final boolean[][] CAN_MOVE = {
    {false, true, true},
    {true, false, false},
    {true, false, false},
  };

  @Test
  void transitionTable() {
    TaskStatus[] all = TaskStatus.values();
    assertThat(all).hasSize(3);
    for (int from = 0; from < all.length; from++) {
      for (int to = 0; to < all.length; to++) {
        assertThat(all[from].canMoveTo(all[to]))
            .as("%s -> %s", all[from], all[to])
            .isEqualTo(CAN_MOVE[from][to]);
      }
    }
  }

  @Test
  void overdueIsOpenWithADueTimeBeforeNow() {
    Instant now = Instant.parse("2031-01-01T12:00:00Z");
    CrmTask task = task(now.minusNanos(1));
    assertThat(task.isOverdue(now)).isTrue();
    // The due time itself is not yet late.
    assertThat(task(now).isOverdue(now)).isFalse();
    assertThat(task(now.plusSeconds(1)).isOverdue(now)).isFalse();
    assertThat(task(null).isOverdue(now)).isFalse();
    task.changeStatus(TaskStatus.COMPLETED);
    assertThat(task.isOverdue(now)).isFalse();
    task.changeStatus(TaskStatus.OPEN);
    assertThat(task.isOverdue(now)).isTrue();
    task.changeStatus(TaskStatus.CANCELLED);
    assertThat(task.isOverdue(now)).isFalse();
  }

  @Test
  void onlyCompletingRecordsATimeInMicroseconds() {
    CrmTask task = task(null);
    assertThat(task.getCompletedAt()).isNull();
    task.changeStatus(TaskStatus.COMPLETED);
    assertThat(task.getCompletedAt()).isNotNull();
    assertThat(task.getCompletedAt().getNano() % 1000).isZero();
    task.changeStatus(TaskStatus.OPEN);
    assertThat(task.getCompletedAt()).isNull();
    task.changeStatus(TaskStatus.COMPLETED);
    task.changeStatus(TaskStatus.OPEN);
    task.changeStatus(TaskStatus.CANCELLED);
    assertThat(task.getCompletedAt()).isNull();
  }

  @Test
  void theDueTimeIsKeptInMicroseconds() {
    CrmTask task = task(Instant.parse("2031-03-04T10:15:30.123456789Z"));
    assertThat(task.getDueAt()).isEqualTo(Instant.parse("2031-03-04T10:15:30.123456Z"));
  }

  private static CrmTask task(Instant dueAt) {
    CrmTask task = new CrmTask(1);
    task.update("Call", null, dueAt, Priority.MEDIUM, null, null, null, null);
    return task;
  }
}
