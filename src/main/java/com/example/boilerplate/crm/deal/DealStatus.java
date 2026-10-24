package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.settings.StageKind;

/** A deal's status: the kind of the stage it is in, under the name the API gives it. */
public enum DealStatus {
  OPEN,
  WON,
  LOST;

  public static DealStatus of(StageKind kind) {
    return valueOf(kind.name());
  }

  public StageKind kind() {
    return StageKind.valueOf(name());
  }
}
