package com.example.boilerplate.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.boilerplate.support.AbstractIntegrationTest;
import com.example.boilerplate.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class FileIT extends AbstractIntegrationTest {
  static final byte[] PNG = FileSignaturesTest.PNG;

  @Autowired StoredFileRepository files;
  @Autowired UserRepository users;
  @Autowired FileProperties properties;
  private String token;

  @BeforeEach
  void newUser() throws Exception {
    token = registerAndGetAccessToken();
  }

  @Test
  void uploadListDownloadDelete() throws Exception {
    long id = uploadId(token, new MockMultipartFile("file", "chart.png", "image/png", PNG));

    mvc.perform(auth(get("/api/v1/files/{id}", id), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("chart.png"))
        .andExpect(jsonPath("$.contentType").value("image/png"))
        .andExpect(jsonPath("$.size").value(PNG.length));
    mvc.perform(auth(get("/api/v1/files"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));
    mvc.perform(auth(get("/api/v1/files/{id}/content", id), token))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(PNG))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("attachment;"),
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''chart.png"))))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"));

    Path blob = blob(id);
    assertThat(blob).exists();
    mvc.perform(auth(delete("/api/v1/files/{id}", id), token)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/files/{id}", id), token)).andExpect(status().isNotFound());
    assertThat(blob).doesNotExist();
  }

  @Test
  void usersCannotSeeOrDeleteEachOthersFiles() throws Exception {
    long id = uploadId(token, text("private.txt", "secret"));
    String other = registerAndGetAccessToken();

    mvc.perform(auth(get("/api/v1/files/{id}", id), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/files/{id}/content", id), other))
        .andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/files/{id}", id), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/files"), other))
        .andExpect(jsonPath("$.content.length()").value(0));
    mvc.perform(auth(get("/api/v1/files/{id}", id), token)).andExpect(status().isOk());
  }

  @Test
  void rejectsDisallowedOrDisguisedFiles() throws Exception {
    upload(token, new MockMultipartFile("file", "page.html", "text/html", "<p>".getBytes()))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.startsWith("File type is not allowed")));
    upload(token, new MockMultipartFile("file", "x.png", "image/png", "<script>".getBytes()))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("File content does not match its type image/png"));
    upload(token, new MockMultipartFile("file", "blob", null, PNG))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void rejectsEmptyMissingAndOversizedUploads() throws Exception {
    upload(token, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("File is empty"));
    upload(token, new MockMultipartFile("other", "a.txt", "text/plain", "a".getBytes()))
        .andExpect(status().isBadRequest());
    byte[] big = new byte[(int) properties.maxSize().toBytes() + 1];
    java.util.Arrays.fill(big, (byte) 'a');
    upload(token, new MockMultipartFile("file", "big.txt", "text/plain", big))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413));
    mvc.perform(
            auth(post("/api/v1/files"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void storesOnlyTheBaseName() throws Exception {
    long id = uploadId(token, text("../../etc/passwd.txt", "x"));
    mvc.perform(auth(get("/api/v1/files/{id}", id), token))
        .andExpect(jsonPath("$.name").value("passwd.txt"));
  }

  @Test
  void deletingTheOwnerRemovesTheirStoredFiles() throws Exception {
    String email = uniqueEmail();
    String owner = register(email).get("accessToken").asText();
    Path blob = blob(uploadId(owner, text("notes.txt", "hello")));
    assertThat(blob).exists();

    mvc.perform(
            auth(
                delete("/api/v1/users/{id}", users.findByEmail(email).orElseThrow().getId()),
                adminAccessToken()))
        .andExpect(status().isNoContent());

    assertThat(blob).doesNotExist();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(multipart("/api/v1/files").file(text("a.txt", "a")))
        .andExpect(status().isUnauthorized());
  }

  private Path blob(long id) {
    return properties.storageDir().resolve(files.findById(id).orElseThrow().getStorageKey());
  }

  private long uploadId(String accessToken, MockMultipartFile file) throws Exception {
    String body =
        upload(accessToken, file)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private ResultActions upload(String accessToken, MockMultipartFile file) throws Exception {
    return mvc.perform(auth(multipart("/api/v1/files").file(file), accessToken));
  }

  private static MockMultipartFile text(String name, String content) {
    return new MockMultipartFile(
        "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  private static MockHttpServletRequestBuilder auth(
      MockHttpServletRequestBuilder request, String accessToken) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
  }
}
