package com.example.boilerplate.file;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
  Page<StoredFile> findByOwnerId(long ownerId, Pageable pageable);

  Optional<StoredFile> findByIdAndOwnerId(Long id, long ownerId);

  @Query("select f.storageKey from StoredFile f where f.owner.id = :ownerId")
  List<String> findStorageKeysByOwnerId(@Param("ownerId") Long ownerId);
}
