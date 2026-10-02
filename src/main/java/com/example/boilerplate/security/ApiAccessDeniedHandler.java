package com.example.boilerplate.security;

import com.example.boilerplate.common.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Valid token without the role an endpoint needs: 403 with the usual ApiError body. */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
  private final ObjectMapper json;

  public ApiAccessDeniedHandler(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    HttpStatus status = HttpStatus.FORBIDDEN;
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            "Access denied",
            request.getRequestURI(),
            Map.of()));
  }
}
