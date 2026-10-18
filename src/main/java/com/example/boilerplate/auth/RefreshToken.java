package com.example.boilerplate.auth;

import com.example.boilerplate.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** The sign-in this token descends from, through rotations. */
  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "family_created_at", nullable = false, updatable = false)
  private Instant familyCreatedAt;

  /** When it was rotated; a used token never rotates again. */
  @Column(name = "used_at")
  private Instant usedAt;

  protected RefreshToken() {}

  public RefreshToken(
      User user, String tokenHash, Instant expiresAt, UUID familyId, Instant familyCreatedAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.familyId = familyId;
    this.familyCreatedAt = familyCreatedAt;
  }

  public void markUsed(Instant now) {
    this.usedAt = now;
  }

  public User getUser() {
    return user;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getFamilyCreatedAt() {
    return familyCreatedAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }
}
