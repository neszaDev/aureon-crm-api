package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WorkspaceAccessInterceptorTest {
  private final WorkspaceAccessInterceptor interceptor =
      new WorkspaceAccessInterceptor(mock(WorkspaceMemberRepository.class));
  private final MockHttpServletRequest req = new MockHttpServletRequest();
  private final MockHttpServletResponse res = new MockHttpServletResponse();

  @AfterEach
  void clear() {
    WorkspaceContext.clear();
  }

  @Test
  void asyncHandlingClearsTheContextOfTheRequestThread() {
    WorkspaceContext.set(new Membership(1, 2, "me@example.com", WorkspaceRole.OWNER));
    interceptor.afterConcurrentHandlingStarted(req, res, new Object());
    assertThat(WorkspaceContext.current()).isEmpty();
  }

  @Test
  void completionClearsTheContextEvenAfterAnException() {
    WorkspaceContext.set(new Membership(1, 2, "me@example.com", WorkspaceRole.OWNER));
    interceptor.afterCompletion(req, res, new Object(), new IllegalStateException("boom"));
    assertThat(WorkspaceContext.current()).isEmpty();
  }
}
