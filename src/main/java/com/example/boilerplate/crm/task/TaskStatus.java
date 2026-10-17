package com.example.boilerplate.crm.task;

/**
 * Where a task stands: an open task is completed or cancelled, and either can be reopened. Nothing
 * else moves, so a move to the status a task has already is a conflict, not a no-op.
 */
public enum TaskStatus {
  OPEN,
  COMPLETED,
  CANCELLED;

  public boolean canMoveTo(TaskStatus target) {
    return this == OPEN ? target != OPEN : target == OPEN;
  }
}
