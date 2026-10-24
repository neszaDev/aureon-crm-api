package com.example.boilerplate.crm.task;

import com.example.boilerplate.crm.task.dto.RemindersResponse;
import org.springframework.web.bind.annotation.*;

// workspaceId is declared for the API docs; WorkspaceAccessInterceptor has already resolved it.
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/reminders")
public class ReminderController {
  private final ReminderService service;

  public ReminderController(ReminderService service) {
    this.service = service;
  }

  @GetMapping
  RemindersResponse reminders(@PathVariable long workspaceId) {
    return service.reminders();
  }
}
