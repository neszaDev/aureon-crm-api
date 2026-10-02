package com.example.boilerplate.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Stores each file as {@code <app.files.storage-dir>/<key>}. Single instance only. */
@Component
public class LocalFileStorage implements FileStorage {
  // Keys are UUIDs; anything else could escape the directory.
  private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
  private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

  private final Path root;

  public LocalFileStorage(FileProperties properties) {
    this.root = properties.storageDir().toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new IllegalStateException(
          "app.files.storage-dir (FILES_DIR) " + root + " cannot be created", e);
    }
    if (!Files.isWritable(root))
      throw new IllegalStateException(
          "app.files.storage-dir (FILES_DIR) " + root + " is not writable");
  }

  @Override
  public void store(String key, InputStream content) {
    Path target = path(key);
    try {
      // Write to a temporary file first so a half-written upload is never visible under its key.
      Path temp = Files.createTempFile(root, ".upload-", ".tmp");
      try (content) {
        Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store file " + key, e);
    }
  }

  @Override
  public Optional<Resource> load(String key) {
    Path path = path(key);
    return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (IOException e) {
      log.warn("Could not delete stored file {}", key, e);
    }
  }

  private Path path(String key) {
    if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid storage key");
    return root.resolve(key);
  }
}
