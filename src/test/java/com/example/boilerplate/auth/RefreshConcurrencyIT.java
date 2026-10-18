package com.example.boilerplate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.boilerplate.common.exception.InvalidRefreshTokenException;
import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.support.AbstractIntegrationTest;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.user.dto.ChangePasswordRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Refresh tokens under concurrency, through {@link AuthService} (an {@link
 * InvalidRefreshTokenException} is the endpoint's 401). Ported from the security review's probes.
 */
class RefreshConcurrencyIT extends AbstractIntegrationTest {
  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private final ExecutorService pool = Executors.newFixedThreadPool(6);

  @AfterEach
  void shutDown() {
    pool.shutdownNow();
  }

  /** Probe P1: tabs refreshing with one token at once; one wins, the session goes on. */
  @Test
  void parallelRefreshesWithOneTokenHaveOneWinnerAndRevokeNothing() throws Exception {
    for (int round = 0; round < 5; round++) {
      String token = register(uniqueEmail()).get("refreshToken").asText();
      List<String> results = all(List.of(token, token, token, token, token, token));

      assertThat(results).filteredOn("401"::equals).as("round %d", round).hasSize(5);
      String winner = results.stream().filter(r -> !r.equals("401")).findFirst().orElseThrow();
      assertThat(auth.refresh(winner).refreshToken()).isNotBlank();
    }
  }

  /**
   * Probe P2: a thief rotates in a tight loop while the victim changes the password. Before token
   * versions, the change's {@code DELETE ... WHERE user_id} didn't see the row the thief's
   * in-flight rotation inserted, so the thief kept refreshing, in 15 runs out of 15.
   */
  @Test
  void aPasswordChangeEndsAThiefWhoKeepsRefreshing() throws Exception {
    List<String> outcomes = new ArrayList<>();
    for (int round = 0; round < 10; round++) {
      String email = uniqueEmail();
      JsonNode registered = register(email);
      long id = users.findByEmail(email).orElseThrow().getId();
      AtomicReference<String> refresh =
          new AtomicReference<>(registered.get("refreshToken").asText());
      AtomicReference<String> access =
          new AtomicReference<>(registered.get("accessToken").asText());
      AtomicBoolean changed = new AtomicBoolean();
      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger rotations = new AtomicInteger();
      AtomicInteger rotationsStartedAfterTheChange = new AtomicInteger();
      AtomicReference<String> error = new AtomicReference<>();
      Thread thief =
          new Thread(
              () -> {
                while (!stop.get()) {
                  boolean startedAfter = changed.get();
                  try {
                    var tokens = auth.refresh(refresh.get());
                    refresh.set(tokens.refreshToken());
                    access.set(tokens.accessToken());
                    rotations.incrementAndGet();
                    if (startedAfter) rotationsStartedAfterTheChange.incrementAndGet();
                  } catch (InvalidRefreshTokenException e) {
                    return;
                  } catch (RuntimeException e) {
                    error.set(e.toString());
                    return;
                  }
                }
              });
      thief.start();
      Thread.sleep(80);
      auth.changePassword(id, new ChangePasswordRequest(PASSWORD, "another-password-" + round));
      changed.set(true);
      Thread.sleep(300);
      stop.set(true);
      thief.join(5000);
      int me =
          mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(access.get())))
              .andReturn()
              .getResponse()
              .getStatus();
      outcomes.add(
          "rotations=%d afterChange=%d me=%d error=%s"
              .formatted(rotations.get(), rotationsStartedAfterTheChange.get(), me, error.get()));
    }

    assertThat(outcomes)
        .allSatisfy(
            o ->
                assertThat(o)
                    .doesNotStartWith("rotations=0 ")
                    .contains(" afterChange=0 ", " me=401 ", " error=null"));
  }

  /**
   * Probe P5: every sign-in and rotation deletes the expired rows. As a derived delete (select,
   * then one delete per row), concurrent refreshes deleted the same row and lost with a 409.
   */
  @Test
  void concurrentRefreshesDeletingTheSameExpiredRowsAllSucceed() throws Exception {
    List<String> failures = new ArrayList<>();
    for (int round = 0; round < 8; round++) {
      List<String> expiring = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        expiring.add(register(uniqueEmail()).get("refreshToken").asText());
      List<String> tokens = new ArrayList<>();
      for (int i = 0; i < 6; i++) tokens.add(register(uniqueEmail()).get("refreshToken").asText());
      for (String t : expiring)
        jdbc.update(
            "update refresh_tokens set expires_at = now() - interval '1 minute'"
                + " where token_hash = ?",
            Tokens.sha256Hex(t));

      all(tokens).stream().filter(r -> r.startsWith("ERR")).forEach(failures::add);
    }

    assertThat(failures).isEmpty();
  }

  /**
   * Refreshes every token at once: the new refresh token, {@code 401} or {@code ERR: <exception>}.
   */
  private List<String> all(List<String> tokens) throws Exception {
    CountDownLatch go = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    for (String token : tokens)
      futures.add(
          pool.submit(
              () -> {
                go.await();
                try {
                  return auth.refresh(token).refreshToken();
                } catch (InvalidRefreshTokenException e) {
                  return "401";
                } catch (RuntimeException e) {
                  return "ERR: " + e;
                }
              }));
    go.countDown();
    List<String> results = new ArrayList<>();
    for (Future<String> f : futures) results.add(f.get());
    return results;
  }
}
