package com.example.boilerplate.file;

import com.example.boilerplate.common.entity.AuditableEntity;
import com.example.boilerplate.user.User;
import jakarta.persistence.*;

/** Metadata of an uploaded file; the bytes live in {@link FileStorage} under {@code storageKey}. */
@Entity
@Table(name = "stored_files")
public class StoredFile extends AuditableEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(nullable = false, unique = true, length = 64, updatable = false)
  private String storageKey;

  @Column(nullable = false)
  private String originalName;

  @Column(nullable = false, length = 127)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long size;

  protected StoredFile() {}

  public StoredFile(
      User owner, String storageKey, String originalName, String contentType, long size) {
    this.owner = owner;
    this.storageKey = storageKey;
    this.originalName = originalName;
    this.contentType = contentType;
    this.size = size;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getOriginalName() {
    return originalName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSize() {
    return size;
  }
}
