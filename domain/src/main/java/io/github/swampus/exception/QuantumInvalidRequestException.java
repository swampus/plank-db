package io.github.swampus.exception;

public class QuantumInvalidRequestException extends AppException {
    public QuantumInvalidRequestException(String message) {
        super(message);
    }

    public QuantumInvalidRequestException(String message, Exception e) {
        super(message, e);
    }
}
