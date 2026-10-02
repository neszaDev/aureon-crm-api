package com.example.boilerplate.file;

import com.example.boilerplate.common.exception.FileRejectedException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.file.dto.FileResponse;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserDeletionEvent;
import com.example.boilerplate.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Owner-scoped uploads. Rows change inside the transaction; stored bytes are removed only once it
 * has committed (or, for a new upload, once it has rolled back), so the two never disagree.
 */
@Service
@Transactional
public class FileService {
  private static final Logger log = LoggerFactory.getLogger(FileService.class);

  private final StoredFileRepository files;
  private final UserRepository users;
  private final FileStorage storage;
  private final FileProperties properties;

  public FileService(
      StoredFileRepository files,
      UserRepository users,
      FileStorage storage,
      FileProperties properties) {
    this.files = files;
    this.users = users;
    this.storage = storage;
    this.properties = properties;
  }

  /** The file's metadata and its bytes. */
  public record Download(FileResponse file, Resource content) {}

  public FileResponse upload(String email, MultipartFile upload) {
    if (upload.isEmpty()) throw new FileRejectedException(HttpStatus.BAD_REQUEST, "File is empty");
    if (upload.getSize() > properties.maxSize().toBytes())
      throw new FileRejectedException(
          HttpStatus.PAYLOAD_TOO_LARGE, "File is larger than " + properties.maxSize());
    String type = normalizeType(upload.getContentType());
    if (!properties.allowedTypes().contains(type))
      throw new FileRejectedException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "File type is not allowed. Allowed: " + String.join(", ", properties.allowedTypes()));
    if (!FileSignatures.matches(type, head(upload)))
      throw new FileRejectedException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content does not match its type " + type);

    User owner =
        users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    String key = UUID.randomUUID().toString();
    try (InputStream content = upload.getInputStream()) {
      storage.store(key, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    afterRollback(() -> storage.delete(key));
    return response(
        files.save(
            new StoredFile(
                owner, key, safeName(upload.getOriginalFilename()), type, upload.getSize())));
  }

  @Transactional(readOnly = true)
  public Page<FileResponse> list(String email, int page, int size) {
    return files
        .findByOwnerEmail(
            email, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(FileService::response);
  }

  @Transactional(readOnly = true)
  public FileResponse get(String email, Long id) {
    return response(file(email, id));
  }

  @Transactional(readOnly = true)
  public Download download(String email, Long id) {
    StoredFile file = file(email, id);
    Resource content =
        storage
            .load(file.getStorageKey())
            .orElseThrow(
                () -> {
                  log.error("Stored bytes missing for file {}", file.getId());
                  return new NotFoundException("File not found");
                });
    return new Download(response(file), content);
  }

  public void delete(String email, Long id) {
    StoredFile file = file(email, id);
    files.delete(file);
    afterCommit(List.of(file.getStorageKey()));
  }

  /** The user's rows cascade in the database; their bytes go once the deletion has committed. */
  @EventListener
  void deleteFilesOf(UserDeletionEvent event) {
    afterCommit(files.findStorageKeysByOwnerId(event.userId()));
  }

  private StoredFile file(String email, Long id) {
    return files
        .findByIdAndOwnerEmail(id, email)
        .orElseThrow(() -> new NotFoundException("File not found"));
  }

  private void afterCommit(List<String> keys) {
    if (keys.isEmpty()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            keys.forEach(storage::delete);
          }
        });
  }

  private static void afterRollback(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) action.run();
          }
        });
  }

  private static byte[] head(MultipartFile upload) {
    try (InputStream in = upload.getInputStream()) {
      return in.readNBytes(FileSignatures.HEAD_BYTES);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code "Image/PNG; charset=x"} → {@code "image/png"}; missing → octet-stream (not allowed). */
  static String normalizeType(String contentType) {
    if (contentType == null || contentType.isBlank()) return "application/octet-stream";
    int params = contentType.indexOf(';');
    return (params < 0 ? contentType : contentType.substring(0, params))
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  /** The client's file name without any path, control characters or excess length. */
  static String safeName(String original) {
    String name = original == null ? "" : original;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("\\p{Cntrl}", "").strip();
    if (name.isEmpty() || name.equals(".") || name.equals("..")) return "file";
    return name.length() <= 255 ? name : name.substring(name.length() - 255);
  }

  private static FileResponse response(StoredFile f) {
    return new FileResponse(
        f.getId(), f.getOriginalName(), f.getContentType(), f.getSize(), f.getCreatedAt());
  }
}
