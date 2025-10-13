package io.github.swampus.controller.advice;

import io.github.swampus.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    // -- helpers ----------------------------------------------------------------

    private static String nowUtcIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString(); // RFC 3339 string
    }

    private static ApiError errorBody(HttpStatus status, String message, String path) {
        return new ApiError(
                nowUtcIso(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
    }

    // -- mappings ----------------------------------------------------------------

    @ExceptionHandler(QuantumInvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalid(QuantumInvalidRequestException ex, HttpServletRequest req) {
        var status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(status, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({CollectionNotFoundException.class, KeyNotFoundException.class, RangeNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        var status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(errorBody(status, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(QuantumExternalServiceException.class)
    public ResponseEntity<ApiError> handleUpstream(QuantumExternalServiceException ex, HttpServletRequest req) {
        var status = HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(errorBody(status, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(QuantumIllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(QuantumIllegalStateException ex, HttpServletRequest req) {
        var status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(errorBody(status, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(errorBody(status, String.valueOf(ex.getMessage()), req.getRequestURI()));
    }

    // -- payload -----------------------------------------------------------------

    @Builder
    public record ApiError(
            String timestamp,
            int status,
            String error,
            String message,
            String path
    ) {}
}
