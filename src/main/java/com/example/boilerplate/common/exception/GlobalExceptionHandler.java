package com.example.boilerplate.common.exception;

import com.example.boilerplate.common.web.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest r) {
    return error(HttpStatus.CONFLICT, e.getMessage(), r, Map.of());
  }

  /** A unique constraint lost a race with a concurrent request (e.g. two sign-ups, one email). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest r) {
    log.warn("Constraint violation on {} {}", r.getMethod(), route(r));
    return error(HttpStatus.CONFLICT, "Conflicts with existing data", r, Map.of());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ApiError> staleUpdate(OptimisticLockingFailureException e, HttpServletRequest r) {
    return error(
        HttpStatus.CONFLICT, "Changed by another request; reload and try again", r, Map.of());
  }

  /** Thrown by services that re-check a role (e.g. UserAdminService). */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException e, HttpServletRequest r) {
    return error(HttpStatus.FORBIDDEN, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(RateLimitExceededException.class)
  ResponseEntity<ApiError> tooManyRequests(RateLimitExceededException e, HttpServletRequest r) {
    HttpStatus s = HttpStatus.TOO_MANY_REQUESTS;
    return ResponseEntity.status(s)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
        .body(
            new ApiError(
                Instant.now(),
                s.value(),
                s.getReasonPhrase(),
                e.getMessage(),
                RequestPaths.safePath(r),
                Map.of()));
  }

  @ExceptionHandler(FileRejectedException.class)
  ResponseEntity<ApiError> fileRejected(FileRejectedException e, HttpServletRequest r) {
    return error(e.getStatus(), e.getMessage(), r, Map.of());
  }

  /** Over spring.servlet.multipart limits, before the controller runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest r) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large", r, Map.of());
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest r) {
    return error(HttpStatus.NOT_FOUND, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiError> unauthorized(BadCredentialsException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), r, Map.of());
  }

  /** A valid token whose user no longer exists (e.g. deleted account). */
  @ExceptionHandler(UsernameNotFoundException.class)
  ResponseEntity<ApiError> unknownUser(UsernameNotFoundException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, "Authentication required", r, Map.of());
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  ResponseEntity<ApiError> invalidRefreshToken(
      InvalidRefreshTokenException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, e.getMessage(), r, Map.of());
  }

  /** A field rejected by a service (e.g. a wrong current password), shaped like bean validation. */
  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<ApiError> fieldInvalid(FieldValidationException e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "Validation failed", r, Map.of(e.field(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Validation failed", r, fields);
  }

  /** Constraint violations on @RequestParam/@PathVariable (e.g. {@code size=1000}). */
  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  ResponseEntity<ApiError> parameterValidation(Exception e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "Validation failed", r, Map.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> unreadable(Exception e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, "Malformed request", r, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
    // Spring MVC exceptions (404 no route, 405 method, 415 media type, ...) carry their status.
    if (e instanceof ErrorResponse er) {
      HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
      return error(status, status.getReasonPhrase(), r, Map.of());
    }
    log.error("Unhandled exception on {} {}", r.getMethod(), route(r), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", r, Map.of());
  }

  /**
   * The matched route (e.g. {@code /api/v1/invitations/{token}/accept}), never the raw URI: paths
   * carry secrets such as invitation tokens, and logs must not.
   */
  private static Object route(HttpServletRequest r) {
    Object pattern = r.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern != null ? pattern : "<unmatched>";
  }

  private ResponseEntity<ApiError> error(
      HttpStatus s, String m, HttpServletRequest r, Map<String, String> f) {
    return ResponseEntity.status(s)
        .body(
            new ApiError(
                Instant.now(), s.value(), s.getReasonPhrase(), m, RequestPaths.safePath(r), f));
  }
}
