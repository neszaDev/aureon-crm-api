package com.example.boilerplate.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.web.servlet.HandlerMapping;

/** The {@code path} of an error body: never the raw URI, which can hold an invitation token. */
public final class RequestPaths {
  private static final Pattern INVITATION_TOKEN = Pattern.compile("/invitations/+[^/]+");

  private RequestPaths() {}

  /**
   * The matched route (e.g. {@code /api/v1/invitations/{token}/accept}). Before a route is matched
   * (security's 401 and 403), the URI with any invitation token replaced by {@code {token}}.
   */
  public static String safePath(HttpServletRequest r) {
    if (r.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String route) {
      return route;
    }
    return INVITATION_TOKEN.matcher(r.getRequestURI()).replaceAll("/invitations/{token}");
  }
}
