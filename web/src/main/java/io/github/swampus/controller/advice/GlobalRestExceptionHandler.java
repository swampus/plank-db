package io.github.swampus.controller.advice;

import io.github.swampus.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    /**
     * Feature toggle for including server-side stacktraces in HTTP responses.
     * This MUST be disabled in production to avoid leaking internal details.
     */
    @Value("${app.errors.include-stacktrace:false}")
    private boolean includeStacktrace;

    // ---------- helpers ---------------------------------------------------------

    /** Returns current UTC time as RFC 3339 string to make timestamps locale-agnostic. */
    private static String nowUtcIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    /**
     * Provides a consistent, non-null message for the client.
     * Some exceptions have null/blank messages; returning class name is a safer fallback.
     */
    private static String safeMessage(Exception ex) {
        var msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
    }

    /** Converts a Throwable stack into a string; used only when explicitly enabled. */
    private static String stackTraceOf(Throwable t) {
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Builds a structured error payload that:
     *  - correlates with server logs via errorId
     *  - is stable for API consumers (fields do not fluctuate)
     *  - can optionally include stacktrace in non-prod environments
     */
    private ApiError errorBody(HttpStatus status,
                               Exception ex,
                               HttpServletRequest req,
                               String errorId,
                               Object details) {
        return ApiError.builder()
                .timestamp(nowUtcIso())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(safeMessage(ex))
                .path(req.getRequestURI())
                .exceptionClass(ex.getClass().getName())
                .errorId(errorId)
                .trace(includeStacktrace ? stackTraceOf(ex) : null)
                .details(details)
                .build();
    }

    /**
     * Centralizes response creation + error logging.
     * Always logs with the throwable (so stacktrace is in server logs) and the errorId for correlation.
     */
    private ResponseEntity<ApiError> respond(HttpStatus status,
                                             Exception ex,
                                             HttpServletRequest req,
                                             Object details) {
        var errorId = UUID.randomUUID().toString();
        // Log at ERROR level with full throwable; the last `ex` argument ensures stacktrace is printed.
        log.error("HTTP {} {} -> [{}] {} | {}",
                status.value(), status.getReasonPhrase(), errorId, req.getRequestURI(), ex.toString(), ex);
        return ResponseEntity.status(status).body(errorBody(status, ex, req, errorId, details));
    }

    // ---------- 400 family: validation / payload issues ------------------------

    /**
     * Bean validation failure on @RequestBody payloads with @Valid.
     * Maps field-level errors into a machine-readable structure under "details.fieldErrors".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "rejectedValue", Objects.requireNonNull(fe.getRejectedValue()),
                        "message", Objects.requireNonNull(fe.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ex, req, Map.of("fieldErrors", details));
    }

    /** Binding failure for non-body parameters (e.g., @ModelAttribute, query params). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", Objects.requireNonNull(fe.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ex, req, Map.of("fieldErrors", details));
    }

    /** Constraint violations from method-level validation (@Validated on controllers/services). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        var details = ex.getConstraintViolations().stream()
                .map(v -> Map.of("property", String.valueOf(v.getPropertyPath()), "message", v.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ex, req, Map.of("violations", details));
    }

    /**
     * Unreadable HTTP message (malformed JSON, wrong numeric types, empty body with required @RequestBody, etc.).
     * Returning 400 tells the client to fix the payload; 500 would be misleading.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ex, req, null);
    }

    // ---------- domain: 404/409/502 --------------------------------------------

    /** Client sent a semantically invalid request (e.g., impossible range filters). */
    @ExceptionHandler(QuantumInvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalid(QuantumInvalidRequestException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ex, req, null);
    }

    /** Resources not found should be 404, not 500. */
    @ExceptionHandler({CollectionNotFoundException.class, KeyNotFoundException.class, RangeNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, ex, req, null);
    }

    /**
     * Surface upstream errors (e.g., IBM Quantum calls) as 502 Bad Gateway.
     * This clearly indicates the failure is in a dependency, not the caller's fault.
     */
    @ExceptionHandler(QuantumExternalServiceException.class)
    public ResponseEntity<ApiError> handleUpstream(QuantumExternalServiceException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_GATEWAY, ex, req, null);
    }

    /**
     * Illegal state conflicts (e.g., mode requires token, but token missing).
     * 409 conveys a state conflict rather than a generic server crash.
     */
    @ExceptionHandler(QuantumIllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(QuantumIllegalStateException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, ex, req, null);
    }

    // ---------- infra: 405 / 415 ------------------------------------------------

    /** Wrong HTTP method (e.g., GET on a POST-only endpoint). Return 405 with allowed methods. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ex, req, Map.of(
                "method", ex.getMethod(),
                "supported", Objects.requireNonNull(ex.getSupportedHttpMethods())
        ));
    }

    /** Unsupported Content-Type (e.g., text/plain where application/json is required). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex, req, Map.of(
                "contentType", ex.getContentType(),
                "supported", ex.getSupportedMediaTypes()
        ));
    }

    // ---------- last line of defense: 500 --------------------------------------

    /**
     * Catch-all handler to prevent framework default HTML error pages
     * and to keep API responses JSON-shaped and correlatable via errorId.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ex, req, null);
    }

    // ---------- payload ---------------------------------------------------------

    /**
     * Stable, client-facing error envelope.
     * Keep fields stable across versions; adding optional fields is OK, removing/renaming breaks clients.
     */
    @Builder
    public record ApiError(
            String timestamp,      // RFC 3339, UTC
            int status,            // 4xx/5xx numeric code
            String error,          // reason phrase, e.g. "Bad Request"
            String message,        // safe, non-null human-friendly message
            String path,           // request URI
            String exceptionClass, // FQCN of exception for diagnostics
            String errorId,        // correlates with server logs
            String trace,          // nullable: stacktrace when enabled for non-prod
            Object details         // nullable: structured context (validation errors, etc.)
    ) {}
}
