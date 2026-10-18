package com.example.boilerplate.crm.deal;

import com.example.boilerplate.crm.common.MoneyTotal;
import com.example.boilerplate.crm.deal.DealTotals.StageTotal;
import com.example.boilerplate.crm.deal.dto.*;
import com.example.boilerplate.crm.settings.PipelineStageService;
import com.example.boilerplate.crm.settings.dto.StageResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The pipeline board of the current workspace. */
@Service
@Transactional(readOnly = true)
public class DealBoardService {
  static final int DEALS_PER_STAGE = 50;
  private static final Sort RECENT_FIRST = Sort.by(Direction.DESC, "updatedAt", "id");

  private final DealRepository deals;
  private final DealService dealService;
  private final PipelineStageService stages;

  public DealBoardService(
      DealRepository deals, DealService dealService, PipelineStageService stages) {
    this.deals = deals;
    this.dealService = dealService;
    this.stages = stages;
  }

  /**
   * Every stage in position order with the active deals that match ({@code q}, {@code ownerId} and
   * {@code unassigned} as in the list, a 400 on {@code unassigned} with an owner): their count and
   * totals per currency from one grouped query, and the 50 most recently updated, one query per
   * stage that has any.
   */
  public BoardResponse board(String q, Long ownerId, boolean unassigned, Long companyId) {
    Map<Long, Long> counts = new HashMap<>();
    Map<Long, List<MoneyTotal>> totals = new HashMap<>();
    // By currency, so each stage's totals come out sorted.
    for (StageTotal t : deals.totalsByStage(matching(q, null, ownerId, unassigned, companyId))) {
      counts.merge(t.stageId(), t.count(), Long::sum);
      totals
          .computeIfAbsent(t.stageId(), id -> new ArrayList<>())
          .add(new MoneyTotal(t.currency(), t.amount()));
    }
    List<StageResponse> pipeline = stages.list();
    List<Deal> shown = new ArrayList<>();
    for (StageResponse stage : pipeline) {
      if (!counts.containsKey(stage.id())) continue;
      shown.addAll(
          deals.findBy(
              matching(q, stage.id(), ownerId, unassigned, companyId),
              query -> query.sortBy(RECENT_FIRST).limit(DEALS_PER_STAGE).all()));
    }
    // groupingBy keeps each stage's deals in the order they were found.
    Map<Long, List<DealSummary>> cards =
        dealService.summaries(shown).stream().collect(Collectors.groupingBy(d -> d.stage().id()));
    return new BoardResponse(
        pipeline.stream()
            .map(
                stage ->
                    new BoardColumn(
                        stage,
                        counts.getOrDefault(stage.id(), 0L),
                        totals.getOrDefault(stage.id(), List.of()),
                        cards.getOrDefault(stage.id(), List.of())))
            .toList());
  }

  private static Specification<Deal> matching(
      String q, Long stageId, Long ownerId, boolean unassigned, Long companyId) {
    return DealSpecifications.matching(
        new DealFilter(q, stageId, null, ownerId, unassigned, companyId, null, null, null, false));
  }
}
