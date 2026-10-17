package com.example.boilerplate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class RequestPathsTest {
  @Test
  void theMatchedRouteWins() {
    var r = new MockHttpServletRequest("GET", "/api/v1/invitations/secret");
    r.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/invitations/{token}");
    assertThat(RequestPaths.safePath(r)).isEqualTo("/api/v1/invitations/{token}");
  }

  @Test
  void beforeRoutingTheTokenIsMasked() {
    assertThat(RequestPaths.safePath(new MockHttpServletRequest("POST", "/api/v1/users/me")))
        .isEqualTo("/api/v1/users/me");
    for (String uri :
        new String[] {"/api/v1/invitations/secret/accept", "/api/v1/invitations//secret/accept"}) {
      assertThat(RequestPaths.safePath(new MockHttpServletRequest("POST", uri)))
          .isEqualTo("/api/v1/invitations/{token}/accept");
    }
  }
}
