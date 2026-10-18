package com.example.boilerplate.user;

import com.example.boilerplate.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {
  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.USER;

  /** Access tokens issued before this are refused; whole seconds, as a token's {@code iat}. */
  private Instant sessionsValidAfter;

  protected User() {}

  public User(String email, String passwordHash) {
    this.email = email.toLowerCase(Locale.ROOT);
    this.passwordHash = passwordHash;
  }

  public void changeEmail(String email) {
    this.email = email.toLowerCase(Locale.ROOT);
    endSessions();
  }

  public void changeDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
    endSessions();
  }

  public void changeRole(Role role) {
    this.role = role;
    endSessions();
  }

  // shortcut: a token issued in the same second as the change survives it (iat has whole
  // seconds); switch to a per-user token version if that second ever matters.
  private void endSessions() {
    sessionsValidAfter = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public Instant getSessionsValidAfter() {
    return sessionsValidAfter;
  }
}
