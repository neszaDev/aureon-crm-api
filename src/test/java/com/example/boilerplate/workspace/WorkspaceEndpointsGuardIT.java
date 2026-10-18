package com.example.boilerplate.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Takes every endpoint under {@code /api/v1/workspaces/{workspaceId}} from the running application,
 * so a controller added later is covered without touching this class: a non-member gets 404, an
 * anonymous caller 401.
 */
class WorkspaceEndpointsGuardIT extends WorkspaceTestSupport {
  private static final String PREFIX = "/api/v1/workspaces/{workspaceId}";

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  record Endpoint(HttpMethod method, String pattern) {
    @Override
    public String toString() {
      return method + " " + pattern;
    }

    /** {@code workspaceId} as given, every other path variable as {@code 1}. */
    MockHttpServletRequestBuilder build(long workspaceId) {
      String path = pattern.replace("{workspaceId}", String.valueOf(workspaceId));
      return request(method, path.replaceAll("\\{[^/]+}", "1"));
    }

    boolean hasBody() {
      return method == HttpMethod.POST || method == HttpMethod.PUT;
    }
  }

  @Test
  void everyWorkspaceEndpointIs404ForNonMembers() throws Exception {
    long workspace = createWorkspace(tokenFor(uniqueEmail()), "Owned by someone else");
    // A real user with a workspace of their own, so only the membership differs.
    String outsider = tokenFor(uniqueEmail());
    createWorkspace(outsider, "Outsider's own");

    List<String> wrong = new ArrayList<>();
    for (Endpoint e : workspaceEndpoints()) {
      var response =
          (e.hasBody()
                  ? send(outsider, e.build(workspace), Map.of())
                  : send(outsider, e.build(workspace)))
              .andReturn()
              .getResponse();
      // The interceptor's 404, not one a service gave after letting the outsider in.
      String message = json.readTree(response.getContentAsString()).path("message").asText();
      if (response.getStatus() != 404 || !message.equals("Workspace not found")) {
        wrong.add(e + " -> " + response.getStatus() + " " + message);
      }
    }

    assertThat(wrong)
        .as("endpoints that answered a non-member with something but the interceptor's 404")
        .isEmpty();
  }

  @Test
  void everyWorkspaceEndpointIs401WithoutToken() throws Exception {
    long workspace = createWorkspace(tokenFor(uniqueEmail()), "Acme");

    List<String> wrong = new ArrayList<>();
    for (Endpoint e : workspaceEndpoints()) {
      MockHttpServletRequestBuilder request = e.build(workspace);
      if (e.hasBody()) request.contentType("application/json").content("{}");
      int status = mvc.perform(request).andReturn().getResponse().getStatus();
      if (status != 401) wrong.add(e + " -> " + status);
    }

    assertThat(wrong)
        .as("endpoints that answered an anonymous caller with something but 401")
        .isEmpty();
  }

  private List<Endpoint> workspaceEndpoints() {
    List<Endpoint> found = new ArrayList<>();
    mappings
        .getHandlerMethods()
        .keySet()
        .forEach(
            info -> {
              var methods = info.getMethodsCondition().getMethods();
              for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(PREFIX)) continue;
                assertThat(methods).as("methods of %s", pattern).isNotEmpty();
                for (RequestMethod m : methods) {
                  found.add(new Endpoint(HttpMethod.valueOf(m.name()), pattern));
                }
              }
            });
    // The endpoints there are today, so the scan can't pass by missing some: raise it with every
    // new workspace endpoint.
    assertThat(found).hasSizeGreaterThanOrEqualTo(68);
    return found;
  }
}
