package com.example.boilerplate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** A client's X-Request-Id is kept only when it is a plain id; anything else gets a fresh one. */
class RequestCorrelationFilterTest {
  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

  @Test
  void aPlainIdIsKept() throws Exception {
    for (String id : List.of("abc-123_X.y", "a", "x".repeat(64)))
      assertThat(requestId(id)).isEqualTo(id);
  }

  @Test
  void anythingElseIsReplacedWithAFreshId() throws Exception {
    for (String id :
        List.of("", "x".repeat(65), "a b", "id\r\nX-Injected: 1", "<script>", "ü", "a/b")) {
      String sent = requestId(id);
      assertThat(sent).as(id).isNotEqualTo(id).matches("[0-9a-f-]{36}");
    }
    assertThat(requestId(null)).matches("[0-9a-f-]{36}");
  }

  private String requestId(String header) throws Exception {
    var req = new MockHttpServletRequest();
    if (header != null) req.addHeader("X-Request-Id", header);
    var res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    return res.getHeader("X-Request-Id");
  }
}
