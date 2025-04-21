package io.github.swampus.controller.advice;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.exception.QuantumExternalServiceException;
import io.github.swampus.exception.QuantumInvalidInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CollectionNotFoundException.class)
    public ResponseEntity<String> handleCollectionNotFound(CollectionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(QuantumInvalidInputException.class)
    public ResponseEntity<String> handleInvalidInput(QuantumInvalidInputException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(QuantumExternalServiceException.class)
    public ResponseEntity<String> handleExternalService(QuantumExternalServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Quantum backend error: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected server error: " + ex.getMessage());
    }
}
