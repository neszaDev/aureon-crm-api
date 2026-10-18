package com.example.boilerplate.common.exception;

import com.example.boilerplate.common.web.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
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
  private static final String DATA_EXCEPTION_CLASS = "22";
  private static final String CHARACTER_NOT_IN_REPERTOIRE = "22021";

  /**
   * Too long, out of range, a NUL character, an untranslatable character, a date or time the
   * database can't hold (invalid format, out of range).
   */
  private static final Set<String> CLIENT_DATA_EXCEPTIONS =
      Set.of("22001", "22003", CHARACTER_NOT_IN_REPERTOIRE, "22P05", "22007", "22008");

  /**
   * Unique indexes and foreign keys behind a rule the service checks first: a violation lost a race
   * to it (a stage or source deleted while a deal, lead or contact took it).
   */
  private static final Map<String, ErrorCode> CONSTRAINT_RULES =
      Map.of(
          "uq_pipeline_stages_name", ErrorCode.DUPLICATE_NAME,
          "uq_lead_sources_name", ErrorCode.DUPLICATE_NAME,
          "users_email_key", ErrorCode.EMAIL_TAKEN,
          "uq_workspace_invitations_pending", ErrorCode.ALREADY_INVITED,
          "deals_workspace_id_stage_id_fkey", ErrorCode.STAGE_IN_USE,
          "deals_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE,
          "leads_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE,
          "contacts_workspace_id_source_id_fkey", ErrorCode.SOURCE_IN_USE);

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest r) {
    return error(HttpStatus.CONFLICT, e.code(), e.getMessage(), r, Map.of());
  }

  /**
   * A unique constraint lost a race with a concurrent request (e.g. two sign-ups, one email). Some
   * PostgreSQL data exceptions are the client's mistake instead (a value too long or out of range,
   * text with a NUL character, which PostgreSQL refuses in any text column or parameter), and
   * Spring reports them as integrity violations too. The rest of SQLSTATE class 22 (division by
   * zero, an invalid regex, ...) is a server bug: 500. A constraint named in {@link
   * #CONSTRAINT_RULES} answers with that rule's code.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest r) {
    if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
        && sql.getSQLState() != null
        && sql.getSQLState().startsWith(DATA_EXCEPTION_CLASS)) {
      String state = sql.getSQLState();
      if (!CLIENT_DATA_EXCEPTIONS.contains(state)) return generic(e, r);
      // The state, not the message: it can quote the value.
      log.warn("Data exception {} on {} {}", state, r.getMethod(), route(r));
      String message =
          CHARACTER_NOT_IN_REPERTOIRE.equals(state)
              ? "Text must not contain null characters"
              : "Invalid value";
      return error(HttpStatus.BAD_REQUEST, ErrorCode.TEXT_INVALID, message, r, Map.of());
    }
    log.warn("Constraint violation on {} {}", r.getMethod(), route(r));
    return error(
        HttpStatus.CONFLICT, constraintRule(e), "Conflicts with existing data", r, Map.of());
  }

  /**
   * A stale version, or a lock the database took back (PostgreSQL aborts one side of a deadlock,
   * 40P01, and Spring reports it as a pessimistic locking failure, as a lock timeout): the client
   * reloads and retries.
   */
  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ApiError> concurrentUpdate(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.CONFLICT,
        ErrorCode.VERSION_CONFLICT,
        "Changed by another request; reload and try again",
        r,
        Map.of());
  }

  /** Thrown by services that re-check a role or permission (e.g. UserAdminService). */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException e, HttpServletRequest r) {
    ErrorCode code = e instanceof ForbiddenException f ? f.code() : ErrorCode.FORBIDDEN;
    return error(HttpStatus.FORBIDDEN, code, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(RateLimitExceededException.class)
  ResponseEntity<ApiError> tooManyRequests(RateLimitExceededException e, HttpServletRequest r) {
    HttpStatus s = HttpStatus.TOO_MANY_REQUESTS;
    return ResponseEntity.status(s)
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
        .body(
            new ApiError(
                Instant.now(),
                s.value(),
                s.getReasonPhrase(),
                ErrorCode.RATE_LIMITED,
                e.getMessage(),
                RequestPaths.safePath(r),
                Map.of()));
  }

  @ExceptionHandler(FileRejectedException.class)
  ResponseEntity<ApiError> fileRejected(FileRejectedException e, HttpServletRequest r) {
    return error(e.getStatus(), null, e.getMessage(), r, Map.of());
  }

  /** Over spring.servlet.multipart limits, before the controller runs. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest r) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, null, "File is too large", r, Map.of());
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest r) {
    return error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiError> unauthorized(BadCredentialsException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, null, e.getMessage(), r, Map.of());
  }

  /** A valid token whose user no longer exists (e.g. deleted account). */
  @ExceptionHandler(UsernameNotFoundException.class)
  ResponseEntity<ApiError> unknownUser(UsernameNotFoundException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, null, "Authentication required", r, Map.of());
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  ResponseEntity<ApiError> invalidRefreshToken(
      InvalidRefreshTokenException e, HttpServletRequest r) {
    return error(HttpStatus.UNAUTHORIZED, null, e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(BadRequestException.class)
  ResponseEntity<ApiError> badRequest(BadRequestException e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, e.code(), e.getMessage(), r, Map.of());
  }

  /** A field rejected by a service (e.g. a wrong current password), shaped like bean validation. */
  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<ApiError> fieldInvalid(FieldValidationException e, HttpServletRequest r) {
    return error(
        HttpStatus.BAD_REQUEST,
        e.code(),
        "Validation failed",
        r,
        Map.of(e.field(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return error(
        HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", r, fields);
  }

  /** Constraint violations on @RequestParam/@PathVariable (e.g. {@code size=1000}). */
  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  ResponseEntity<ApiError> parameterValidation(Exception e, HttpServletRequest r) {
    return error(
        HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", r, Map.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> unreadable(Exception e, HttpServletRequest r) {
    return error(HttpStatus.BAD_REQUEST, null, "Malformed request", r, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
    // Spring MVC exceptions (404 no route, 405 method, 415 media type, ...) carry their status.
    if (e instanceof ErrorResponse er) {
      HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
      return error(status, null, status.getReasonPhrase(), r, Map.of());
    }
    log.error("Unhandled exception on {} {}", r.getMethod(), route(r), e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, null, "Unexpected server error", r, Map.of());
  }

  /**
   * The matched route (e.g. {@code /api/v1/invitations/{token}/accept}), never the raw URI: paths
   * carry secrets such as invitation tokens, and logs must not.
   */
  private static Object route(HttpServletRequest r) {
    Object pattern = r.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern != null ? pattern : "<unmatched>";
  }

  /** The rule of the constraint Hibernate names (read from PostgreSQL's message), else CONFLICT. */
  private static ErrorCode constraintRule(Throwable e) {
    // shortcut: Hibernate's PostgreSQL dialect parses the constraint name out of the English
    // server message, so localized lc_messages fall back to CONFLICT; upgrade to pgjdbc's
    // ServerErrorMessage.getConstraint() if the database may speak another language.
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof org.hibernate.exception.ConstraintViolationException c
          && c.getConstraintName() != null) {
        return CONSTRAINT_RULES.getOrDefault(c.getConstraintName(), ErrorCode.CONFLICT);
      }
    }
    return ErrorCode.CONFLICT;
  }

  /**
   * Always JSON, whatever the request accepts: with the type preset Spring doesn't negotiate it, so
   * a client accepting only {@code text/csv} gets this body, not a failure to write it (which left
   * Spring and came back as the container's 401).
   */
  private ResponseEntity<ApiError> error(
      HttpStatus s, ErrorCode c, String m, HttpServletRequest r, Map<String, String> f) {
    return ResponseEntity.status(s)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ApiError(
                Instant.now(), s.value(), s.getReasonPhrase(), c, m, RequestPaths.safePath(r), f));
  }
}
