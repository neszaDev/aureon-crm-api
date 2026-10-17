package com.example.boilerplate.workspace;

import com.example.boilerplate.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Runs before every {@code /api/v1/workspaces/{workspaceId}/...} handler: no membership of that
 * workspace (or no such workspace, or an id that isn't one) is a 404, so ids can't be probed. The
 * membership is read from the database on every request, so role changes apply at once.
 */
class WorkspaceAccessInterceptor implements HandlerInterceptor {
  private final WorkspaceMemberRepository members;

  WorkspaceAccessInterceptor(WorkspaceMemberRepository members) {
    this.members = members;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    Principal principal = req.getUserPrincipal();
    long workspaceId = workspaceId(req);
    if (principal == null || workspaceId <= 0) throw notFound();
    WorkspaceContext.set(
        members.findMembership(workspaceId, principal.getName()).orElseThrow(this::notFound));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
    WorkspaceContext.clear();
  }

  /** The {@code workspaceId} path variable, or 0 when it is missing or not a number. */
  private static long workspaceId(HttpServletRequest req) {
    if (req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars
        && vars.get("workspaceId") instanceof String id) {
      try {
        return Long.parseLong(id);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  private NotFoundException notFound() {
    return new NotFoundException("Workspace not found");
  }
}
