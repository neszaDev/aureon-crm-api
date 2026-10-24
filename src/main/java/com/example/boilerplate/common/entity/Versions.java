package com.example.boilerplate.common.entity;

import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import java.util.Objects;

public final class Versions {
  private Versions() {}

  /** Optimistic locking for {@code PUT}: the client must send the version it last read. */
  public static void check(AuditableEntity e, Long expected) {
    if (!Objects.equals(e.getVersion(), expected)) {
      throw new ConflictException(
          ErrorCode.VERSION_CONFLICT, "Changed by another request; reload and try again");
    }
  }
}
