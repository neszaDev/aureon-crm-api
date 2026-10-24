package com.example.boilerplate.crm.insight;

import com.example.boilerplate.crm.insight.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  /**
   * Every member reads it. The dates are strings here so that a malformed one is a 400 on its field
   * ({@link Period#date}), like the period's other rules.
   */
  @GetMapping
  DashboardResponse dashboard(
      @PathVariable long workspaceId,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String from,
      @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false)
          String to) {
    return service.dashboard(Period.date("from", from), Period.date("to", to));
  }
}
