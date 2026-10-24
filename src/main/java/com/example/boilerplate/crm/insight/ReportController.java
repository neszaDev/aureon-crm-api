package com.example.boilerplate.crm.insight;

import com.example.boilerplate.crm.insight.dto.ActivityReport;
import com.example.boilerplate.crm.insight.dto.FollowUpReport;
import com.example.boilerplate.crm.insight.dto.FunnelReport;
import com.example.boilerplate.crm.insight.dto.PipelineReport;
import com.example.boilerplate.crm.insight.dto.WonLostReport;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;

/**
 * Every member reads them. The dates are strings, as on the dashboard, so that a malformed one is a
 * 400 on its field ({@link Period#date}). {@code ownerId}: only that member's records (api.md,
 * Dashboard and reports). workspaceId is declared for the API docs; WorkspaceAccessInterceptor has
 * already resolved it.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/reports")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/pipeline")
  PipelineReport pipeline(
      @PathVariable long workspaceId, @RequestParam(required = false) Long ownerId) {
    return service.pipeline(ownerId);
  }

  @GetMapping("/won-lost")
  WonLostReport wonLost(
      @PathVariable long workspaceId,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String from,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String to,
      @RequestParam(required = false) Long ownerId) {
    return service.wonLost(Period.date("from", from), Period.date("to", to), ownerId);
  }

  @GetMapping("/funnel")
  FunnelReport funnel(
      @PathVariable long workspaceId,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String from,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String to,
      @RequestParam(required = false) Long ownerId) {
    return service.funnel(Period.date("from", from), Period.date("to", to), ownerId);
  }

  @GetMapping("/activities")
  ActivityReport activities(
      @PathVariable long workspaceId,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String from,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String to,
      @RequestParam(required = false) Long ownerId) {
    return service.activities(Period.date("from", from), Period.date("to", to), ownerId);
  }

  @GetMapping("/follow-ups")
  FollowUpReport followUps(
      @PathVariable long workspaceId, @RequestParam(required = false) Long ownerId) {
    return service.followUps(ownerId);
  }
}
