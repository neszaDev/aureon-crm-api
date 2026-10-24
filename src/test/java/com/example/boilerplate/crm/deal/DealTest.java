package com.example.boilerplate.crm.deal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.boilerplate.crm.settings.StageKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DealTest {
  @Test
  void closingSetsTheTimeOnlyWhenTheKindChanges() {
    Deal deal = new Deal(1, null);
    deal.moveTo(2, StageKind.OPEN, StageKind.OPEN, "ignored");
    assertThat(deal.getStageId()).isEqualTo(2);
    assertThat(deal.getClosedAt()).isNull();
    assertThat(deal.getLostReason()).isNull();

    deal.moveTo(3, StageKind.OPEN, StageKind.LOST, "Too expensive");
    Instant lost = deal.getClosedAt();
    assertThat(lost).isNotNull();
    // Microseconds, as PostgreSQL stores it, so the answer equals a later read.
    assertThat(lost.getNano() % 1000).isZero();
    assertThat(deal.getLostReason()).isEqualTo("Too expensive");

    // The same LOST stage with a new reason keeps the close time.
    deal.moveTo(3, StageKind.LOST, StageKind.LOST, "Went with a rival");
    assertThat(deal.getClosedAt()).isSameAs(lost);
    assertThat(deal.getLostReason()).isEqualTo("Went with a rival");

    // LOST to WON is a new close and drops the reason.
    deal.moveTo(4, StageKind.LOST, StageKind.WON, "ignored");
    assertThat(deal.getClosedAt()).isNotNull().isNotSameAs(lost);
    assertThat(deal.getLostReason()).isNull();

    deal.moveTo(2, StageKind.WON, StageKind.OPEN, null);
    assertThat(deal.getClosedAt()).isNull();
  }

  @Test
  void statusIsTheStageKind() {
    for (StageKind kind : StageKind.values()) {
      assertThat(DealStatus.of(kind).kind()).isEqualTo(kind);
      assertThat(DealStatus.of(kind).name()).isEqualTo(kind.name());
    }
    assertThat(DealStatus.values()).hasSameSizeAs(StageKind.values());
  }
}
