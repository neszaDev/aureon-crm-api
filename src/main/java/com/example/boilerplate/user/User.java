package com.example.boilerplate.user;

import com.example.boilerplate.common.entity.AuditableEntity;
import jakarta.persistence.*;

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

  protected User() {}

  public User(String email, String passwordHash) {
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
  }

  public void changeEmail(String email) {
    this.email = email.toLowerCase();
  }

  public void changeDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void changeRole(Role role) {
    this.role = role;
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
}
