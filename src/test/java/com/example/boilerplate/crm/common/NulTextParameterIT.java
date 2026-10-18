package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * PostgreSQL can't hold or compare U+0000, so a NUL character in a text query parameter (the
 * request {@code ?industry=%00}) must be the client's mistake, a 400 like the one {@code q} gives,
 * never a 500. Every text-valued query parameter of the API is in {@link #TEXT_PARAMETERS}, and a
 * second test fails when a controller gains one that is not.
 */
class NulTextParameterIT extends WorkspaceTestSupport {
  static final String W = "/api/v1/workspaces/{workspaceId}";

  /** A GET path and the text parameter that goes to its query. */
  record TextParameter(String path, String name) {
    @Override
    public String toString() {
      return path + " ?" + name;
    }
  }

  static final List<TextParameter> TEXT_PARAMETERS =
      List.of(
          new TextParameter(W + "/companies", "q"),
          new TextParameter(W + "/companies", "industry"),
          new TextParameter(W + "/companies/export", "q"),
          new TextParameter(W + "/companies/export", "industry"),
          new TextParameter(W + "/companies/duplicates", "name"),
          new TextParameter(W + "/companies/duplicates", "website"),
          new TextParameter(W + "/contacts", "q"),
          new TextParameter(W + "/contacts", "tag"),
          new TextParameter(W + "/contacts/export", "q"),
          new TextParameter(W + "/contacts/export", "tag"),
          new TextParameter(W + "/contacts/duplicates", "email"),
          new TextParameter(W + "/contacts/duplicates", "phone"),
          new TextParameter(W + "/leads", "q"),
          new TextParameter(W + "/leads/export", "q"),
          new TextParameter(W + "/deals", "q"),
          new TextParameter(W + "/deals/export", "q"),
          new TextParameter(W + "/deals/board", "q"),
          new TextParameter(W + "/tasks", "q"),
          // The dates are strings in the controllers, so a malformed one is a 400 on its field.
          new TextParameter(W + "/dashboard", "from"),
          new TextParameter(W + "/dashboard", "to"),
          new TextParameter(W + "/reports/won-lost", "from"),
          new TextParameter(W + "/reports/won-lost", "to"),
          new TextParameter(W + "/reports/funnel", "from"),
          new TextParameter(W + "/reports/funnel", "to"),
          new TextParameter(W + "/reports/activities", "from"),
          new TextParameter(W + "/reports/activities", "to"),
          // Admin-only, outside the workspaces.
          new TextParameter("/api/v1/users", "q"));

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  static Stream<TextParameter> textParameters() {
    return TEXT_PARAMETERS.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("textParameters")
  void aNullCharacterIs400(TextParameter p) throws Exception {
    String member = tokenFor(uniqueEmail());
    long w = createWorkspace(member, "Acme");
    String token = p.path().startsWith(W) ? member : adminAccessToken();

    send(token, get(p.path(), w).param(p.name(), "\u0000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors." + p.name()).isNotEmpty());
  }

  @Test
  void everyTextParameterOfTheApiIsSwept() {
    Set<String> found = new TreeSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (info, handler) -> {
              if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) return;
              for (MethodParameter parameter : handler.getMethodParameters()) {
                if (!isTextParameter(parameter)) continue;
                parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
                RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
                String name =
                    annotation != null && !annotation.name().isEmpty()
                        ? annotation.name()
                        : parameter.getParameterName();
                info.getPathPatternsCondition()
                    .getPatternValues()
                    .forEach(path -> found.add(new TextParameter(path, name).toString()));
              }
            });

    assertThat(found.stream().filter(s -> s.startsWith("/api/v1")))
        .containsExactlyInAnyOrderElementsOf(
            TEXT_PARAMETERS.stream().map(TextParameter::toString).toList());
  }

  /** A String that Spring binds from the query: {@code @RequestParam} or no annotation at all. */
  private static boolean isTextParameter(MethodParameter p) {
    return p.getParameterType() == String.class
        && (p.hasParameterAnnotation(RequestParam.class)
            || p.getParameterAnnotations().length == 0);
  }
}
