package com.example.boilerplate.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.WorkspaceMember;
import com.example.boilerplate.workspace.WorkspaceMemberRepository;
import com.example.boilerplate.workspace.WorkspaceRole;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Base for ITs of workspace-scoped endpoints: workspaces and members without invitations. */
public abstract class WorkspaceTestSupport extends AbstractIntegrationTest {
  @Autowired protected WorkspaceMemberRepository members;
  @Autowired protected UserRepository users;

  /** Registers {@code email} and returns its access token. */
  protected String tokenFor(String email) throws Exception {
    return register(email).get("accessToken").asText();
  }

  /** Creates a workspace (EUR, Europe/Paris) owned by the token's user and returns its id. */
  protected long createWorkspace(String token, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/workspaces")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name", name,
                                "defaultCurrency", "EUR",
                                "timezone", "Europe/Paris"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  /** Adds a registered user straight to the database; invitations are the real way in. */
  protected void addMember(long workspaceId, String email, WorkspaceRole role) {
    members.save(
        new WorkspaceMember(workspaceId, users.findByEmail(email).orElseThrow().getId(), role));
  }
}
