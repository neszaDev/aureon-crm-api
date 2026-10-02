package com.example.boilerplate.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} if that account does
 * not exist yet. An existing account is never promoted or changed: there is no email verification,
 * so whoever registered the address first would otherwise become admin.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
  static final int MIN_PASSWORD_LENGTH = 12;
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final AdminProperties properties;
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AdminBootstrap(AdminProperties properties, UserRepository users, PasswordEncoder encoder) {
    this.properties = properties;
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) return;
    String password = properties.password();
    if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > 72)
      // Never include the value: this message ends up in startup logs.
      throw new IllegalStateException(
          "app.admin.password (ADMIN_PASSWORD) must be "
              + MIN_PASSWORD_LENGTH
              + " to 72 characters when ADMIN_EMAIL is set");

    users
        .findByEmail(properties.email().toLowerCase())
        .ifPresentOrElse(
            existing -> {
              if (existing.getRole() != Role.ADMIN)
                log.warn(
                    "ADMIN_EMAIL belongs to existing user {} without the ADMIN role; not promoting"
                        + " it. Promote it from another admin account or in the database.",
                    existing.getId());
            },
            () -> {
              User admin = new User(properties.email(), encoder.encode(password));
              admin.changeRole(Role.ADMIN);
              log.info("Created bootstrap admin user {}", users.save(admin).getId());
            });
  }
}
