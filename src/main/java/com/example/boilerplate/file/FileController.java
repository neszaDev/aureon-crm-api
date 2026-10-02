package com.example.boilerplate.file;

import com.example.boilerplate.file.dto.FileResponse;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/files")
public class FileController {
  private final FileService service;

  public FileController(FileService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  FileResponse upload(Authentication a, @RequestPart("file") MultipartFile file) {
    return service.upload(a.getName(), file);
  }

  @GetMapping
  Page<FileResponse> list(
      Authentication a,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(a.getName(), page, size);
  }

  @GetMapping("/{id}")
  FileResponse get(Authentication a, @PathVariable Long id) {
    return service.get(a.getName(), id);
  }

  /** Always a download, never rendered: uploaded HTML or SVG can't run in the API's origin. */
  @GetMapping("/{id}/content")
  ResponseEntity<Resource> content(Authentication a, @PathVariable Long id) {
    var download = service.download(a.getName(), id);
    FileResponse file = download.file();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(download.content());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(Authentication a, @PathVariable Long id) {
    service.delete(a.getName(), id);
  }
}
