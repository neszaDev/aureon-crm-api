package com.example.boilerplate.workspace;

import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Runs before every {@code /api/v1/workspaces/{workspaceId}/...} handler: no membership of that
 * workspace (or no such workspace, or an id that isn't one) is a 404, so ids can't be probed. The
 * membership is read from the database on every request, so role changes apply at once.
 */
class WorkspaceAccessInterceptor implements AsyncHandlerInterceptor {
  private final WorkspaceMemberRepository members;

  WorkspaceAccessInterceptor(WorkspaceMemberRepository members) {
    this.members = members;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    long workspaceId = workspaceId(req);
    if (!(req.getUserPrincipal() instanceof Authentication a
            && a.getPrincipal() instanceof CurrentUser caller)
        || workspaceId <= 0) throw notFound();
    WorkspaceContext.set(
        members.findMembership(workspaceId, caller.id()).orElseThrow(this::notFound));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
    WorkspaceContext.clear();
  }

  /** Async work (e.g. a streamed body) runs elsewhere; don't leave this request on the thread. */
  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest req, HttpServletResponse res, Object handler) {
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
