package com.example.boilerplate.user;

import com.example.boilerplate.common.entity.AuditableEntity;
import jakarta.persistence.*;
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

  /**
   * Tokens carry the version they were issued with; one of another version is refused. Bumped by an
   * email, role or password change; {@code @Version} keeps two changes from losing one.
   */
  @Column(nullable = false)
  private int tokenVersion;

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

  private void endSessions() {
    tokenVersion++;
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

  public int getTokenVersion() {
    return tokenVersion;
  }
}
