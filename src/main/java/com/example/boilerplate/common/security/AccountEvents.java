package com.example.boilerplate.common.security;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Account security events, one INFO line each with ids and field names, never values: {@code
 * Account event event=account.update actorId=1 targetId=5 fields=[email, role]}; in prod's JSON
 * they are fields too. Its logger is INFO whatever the root level (application.yml).
 */
public final class AccountEvents {
  private static final Logger log = LoggerFactory.getLogger(AccountEvents.class);

  private AccountEvents() {}

  /** {@code actorId} null: nobody signed in did it (a sign-in lockout, a refresh-token replay). */
  public static void record(String event, Long actorId, Long targetId, List<String> fields) {
    log.info(
        "Account event {} {} {} {}",
        kv("event", event),
        kv("actorId", actorId),
        kv("targetId", targetId),
        kv("fields", fields));
  }
}
