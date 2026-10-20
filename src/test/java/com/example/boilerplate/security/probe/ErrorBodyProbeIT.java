package com.example.boilerplate.security.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.support.WorkspaceTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Security review probe (phase 4): every kind of error the API gives (bad credentials, bad tokens,
 * malformed and mistyped input, validation, a database constraint, a database data error, unknown
 * routes and methods, a server error) answers the {@code ApiError} shape and nothing else: no
 * credential or token from the request, no SQL, constraint or stored value, no exception class or
 * stack trace.
 */
class ErrorBodyProbeIT extends WorkspaceTestSupport {
  private static final Set<String> API_ERROR =
      Set.of("timestamp", "status", "error", "code", "message", "path", "fieldErrors");
  private static final List<String> NEVER =
      List.of(
          "Exception",
          "exception",
          "\tat ",
          "java.",
          "org.",
          "jakarta.",
          "SQL",
          "select ",
          "insert ",
          "update ",
          "Detail",
          "Key (",
          "constraint",
          "users_email_key",
          "PSQL",
          "ibernate",
          "trace",
          "Cannot deserialize",
          "No enum constant",
          "Unrecognized token",
          "Page offset");

  @Autowired JdbcTemplate jdbc;

  record Case(String label, MockHttpServletRequestBuilder request, List<String> secrets) {}

  @Test
  void errorBodiesCarryNoSecretSqlOrStackTrace() throws Exception {
    String marker = "m" + UUID.randomUUID().toString().replace("-", "");
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Errors");
    String auth = bearer(token);
    String ws = "/api/v1/workspaces/" + w;

    // A 409 from a real unique-key violation (InvitationIT's way to reach users_email_key).
    String invitee = uniqueEmail();
    tokenFor(invitee);
    JsonNode inv =
        body(
            send(token, post(ws + "/invitations"), Map.of("email", invitee, "role", "SALES_REP"))
                .andExpect(status().isCreated()));
    jdbc.update(
        "UPDATE workspace_invitations SET email = upper(email) WHERE id = ?",
        inv.get("id").asLong());
    String link = inv.get("token").asText();

    List<Case> cases = new ArrayList<>();
    cases.add(
        new Case(
            "wrong password",
            json(
                post("/api/v1/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"" + marker + "\"}"),
            List.of(marker)));
    cases.add(
        new Case(
            "malformed JSON around a password",
            json(
                post("/api/v1/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":" + marker + "}"),
            List.of(marker)));
    cases.add(
        new Case(
            "password of the wrong type",
            json(
                post("/api/v1/auth/register"),
                "{\"email\":\"" + email + "\",\"password\":[\"" + marker + "\"]}"),
            List.of(marker)));
    cases.add(
        new Case(
            "unknown refresh token",
            json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + marker + "\"}"),
            List.of(marker)));
    cases.add(
        new Case(
            "garbage access token",
            get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + marker),
            List.of(marker)));
    cases.add(
        new Case(
            "wrong current password",
            json(
                    post("/api/v1/users/me/password"),
                    "{\"currentPassword\":\"" + marker + "\",\"newPassword\":\"" + marker + "x\"}")
                .header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "id not a number",
            get("/api/v1/workspaces/abc").header(HttpHeaders.AUTHORIZATION, auth),
            List.of()));
    cases.add(
        new Case(
            "unknown sort",
            get(ws + "/companies?sort=" + marker).header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "number of the wrong type",
            json(post(ws + "/companies"), "{\"name\":\"x\",\"employeeCount\":\"" + marker + "\"}")
                .header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "bean validation",
            json(post(ws + "/companies"), "{\"name\":\"\",\"website\":\"" + marker + "\"}")
                .header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "text with NUL (database)",
            json(post(ws + "/companies"), "{\"name\":\"" + marker + "\\u0000\"}")
                .header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "unique key violation (database)",
            json(
                post("/api/v1/invitations/{t}/register", link),
                "{\"password\":\"" + marker + "\"}"),
            List.of(marker, link, invitee, invitee.toUpperCase())));
    cases.add(
        new Case("unknown invitation", get("/api/v1/invitations/{t}", marker), List.of(marker)));
    cases.add(
        new Case(
            "unknown route",
            get("/api/v1/" + marker).header(HttpHeaders.AUTHORIZATION, auth),
            List.of()));
    cases.add(
        new Case(
            "method not allowed",
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, auth),
            List.of()));
    cases.add(
        new Case(
            "unsupported media type",
            put("/api/v1/users/me")
                .contentType(MediaType.TEXT_PLAIN)
                .content(marker)
                .header(HttpHeaders.AUTHORIZATION, auth),
            List.of(marker)));
    cases.add(
        new Case(
            "not found in the workspace",
            get(ws + "/companies/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, auth),
            List.of()));
    // A 500 today (finding S-1): its body must still say nothing.
    cases.add(
        new Case(
            "server error",
            get("/api/v1/files?page=2147483647").header(HttpHeaders.AUTHORIZATION, auth),
            List.of()));

    List<String> wrong = new ArrayList<>();
    for (Case c : cases) {
      MockHttpServletResponse res = mvc.perform(c.request()).andReturn().getResponse();
      String text = res.getContentAsString();
      List<String> problems = new ArrayList<>();
      if (res.getStatus() < 400) problems.add("not an error");
      if (res.getContentType() == null || !res.getContentType().startsWith("application/json")) {
        problems.add("content type " + res.getContentType());
      }
      JsonNode body = json.readTree(text);
      body.fieldNames()
          .forEachRemaining(
              f -> {
                if (!API_ERROR.contains(f)) problems.add("field " + f);
              });
      for (String s : NEVER) if (text.contains(s)) problems.add("contains " + s);
      for (String s : c.secrets()) if (text.contains(s)) problems.add("echoes " + s);
      if (!problems.isEmpty())
        wrong.add(c.label() + " -> " + res.getStatus() + " " + problems + " " + text);
    }
    assertThat(wrong).isEmpty();
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r, String body) {
    return r.contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
