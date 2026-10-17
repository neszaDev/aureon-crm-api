package com.example.boilerplate.workspace;

import java.util.Optional;
import java.util.function.Supplier;

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

  /** Runs {@code work} as {@code m}, outside a transaction, then restores the previous context. */
  public static <T> T runAs(Membership m, Supplier<T> work) {
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
