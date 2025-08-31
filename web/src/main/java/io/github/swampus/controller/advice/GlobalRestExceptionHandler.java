package io.github.swampus.controller.advice;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.exception.QuantumExternalServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


import io.github.swampus.exception.*;
import lombok.Builder;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    @ExceptionHandler(QuantumInvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalid(QuantumInvalidRequestException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_REQUEST, req.getRequestURI());
    }

    @ExceptionHandler({CollectionNotFoundException.class, KeyNotFoundException.class, RangeNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.NOT_FOUND, req.getRequestURI());
    }

    @ExceptionHandler(QuantumExternalServiceException.class)
    public ResponseEntity<ApiError> handleUpstream(QuantumExternalServiceException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.BAD_GATEWAY, req.getRequestURI());
    }

    @ExceptionHandler(QuantumIllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(QuantumIllegalStateException ex, HttpServletRequest req) {
        return build(ex, HttpStatus.CONFLICT, req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, req.getRequestURI());
    }

    private ResponseEntity<ApiError> build(Exception ex, HttpStatus status, String path) {
        ApiError body = ApiError.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getMessage())
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }


    @Builder
    record ApiError(OffsetDateTime timestamp, int status, String error, String message, String path) {
    }
}
