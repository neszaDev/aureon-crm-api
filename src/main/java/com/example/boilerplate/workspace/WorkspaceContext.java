package com.example.boilerplate.workspace;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The membership of the current request, set by {@link WorkspaceAccessInterceptor}. Hibernate reads
 * the workspace id when a session opens, so set it before the transaction starts.
 */
public final class WorkspaceContext {
  private static final ThreadLocal<Membership> CURRENT = new ThreadLocal<>();

  private WorkspaceContext() {}

  public static Optional<Membership> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** For code that only runs under a workspace path; no context there is a programming error. */
  public static Membership require() {
    Membership m = CURRENT.get();
    if (m == null) throw new IllegalStateException("No workspace context");
    return m;
  }

  public static long workspaceId() {
    return require().workspaceId();
  }

  /**
   * Runs {@code work} as {@code m}, then restores the previous context. Refuses to run inside a
   * transaction: its session already holds the tenant it opened with.
   */
  public static <T> T runAs(Membership m, Supplier<T> work) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("WorkspaceContext.runAs must run outside a transaction");
    }
    Membership previous = CURRENT.get();
    CURRENT.set(m);
    try {
      return work.get();
    } finally {
      set(previous);
    }
  }

  static void set(Membership m) {
    if (m == null) clear();
    else CURRENT.set(m);
  }

  static void clear() {
    CURRENT.remove();
  }
}
