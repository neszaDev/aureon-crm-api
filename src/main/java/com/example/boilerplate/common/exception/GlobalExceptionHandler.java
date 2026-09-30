package com.example.boilerplate.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest r) {
    return error(HttpStatus.CONFLICT, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest r) {
    return error(HttpStatus.NOT_FOUND, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiError> unauthorized(BadCredentialsException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  ResponseEntity<ApiError> invalidRefreshToken(
      InvalidRefreshTokenException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Validation failed", r, fields);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", r, Map.of());
  }

  private ResponseEntity<ApiError> error(
      HttpStatus s, String m, HttpServletRequest r, Map<String, String> f) {
    return ResponseEntity.status(s)
        .body(new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), m, r.getRequestURI(), f));
  }
}
