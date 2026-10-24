package com.example.boilerplate.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Refresh-token families: a rotated token is kept as used, a used one presented again after the
 * grace (30 s) revokes its whole family, and a family can't rotate past 90 days. Time passes by
 * moving the rows' timestamps back.
 */
class RefreshReuseIT extends AbstractIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  /**
   * The review's probe: a thief rotates first; the victim's replay must end the thief's session.
   */
  @Test
  void aReplayAfterTheGraceRevokesTheWholeFamily() throws Exception {
    String stolen = register(uniqueEmail()).get("refreshToken").asText();
    String thiefs = rotate(stolen);
    usedSecondsAgo(stolen, 31);

    refresh(stolen).andExpect(status().isUnauthorized());
    refresh(thiefs).andExpect(status().isUnauthorized());
  }

  /** Two tabs refreshing together: the slower one gets a 401, the session goes on. */
  @Test
  void aReplayWithinTheGraceIsRefusedButTheNewerTokenStillWorks() throws Exception {
    String first = register(uniqueEmail()).get("refreshToken").asText();
    String second = rotate(first);
    usedSecondsAgo(first, 25);

    refresh(first).andExpect(status().isUnauthorized());
    rotate(second);
  }

  @Test
  void aFamilyOlderThan90DaysCannotRotate() throws Exception {
    String email = uniqueEmail();
    String token = register(email).get("refreshToken").asText();
    startedDaysAgo(token, 91);

    refresh(token).andExpect(status().isUnauthorized());
    login(email, PASSWORD);
  }

  /** The age counts from the sign-in: rotating doesn't restart it. */
  @Test
  void aRotatedTokenKeepsItsFamilysAge() throws Exception {
    String token = register(uniqueEmail()).get("refreshToken").asText();
    startedDaysAgo(token, 89);
    String rotated = rotate(token);
    startedDaysAgo(rotated, 2);

    refresh(rotated).andExpect(status().isUnauthorized());
  }

  private String rotate(String refreshToken) throws Exception {
    String body =
        refresh(refreshToken)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("refreshToken").asText();
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private void usedSecondsAgo(String refreshToken, int seconds) {
    jdbc.update(
        "update refresh_tokens set used_at = now() - make_interval(secs => ?) where token_hash = ?",
        seconds,
        Tokens.sha256Hex(refreshToken));
  }

  /** Moves the start of the token's family {@code days} further back. */
  private void startedDaysAgo(String refreshToken, int days) {
    jdbc.update(
        """
        update refresh_tokens set family_created_at = family_created_at - make_interval(days => ?)
        where family_id = (select family_id from refresh_tokens where token_hash = ?)""",
        days,
        Tokens.sha256Hex(refreshToken));
  }
}
