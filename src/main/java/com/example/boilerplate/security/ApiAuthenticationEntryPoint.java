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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Missing or invalid access token on a protected endpoint: 401 with the usual ApiError body. */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper json;

  public ApiAuthenticationEntryPoint(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("WWW-Authenticate", "Bearer");
    json.writeValue(
        response.getOutputStream(),
        new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            "Authentication required",
            request.getRequestURI(),
            Map.of()));
  }
}
