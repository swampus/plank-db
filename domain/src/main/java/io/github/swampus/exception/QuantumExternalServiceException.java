package io.github.swampus.exception;

public class QuantumExternalServiceException extends AppException {
    public QuantumExternalServiceException(String message) {
        super(message);
    }

    public QuantumExternalServiceException(String message, Exception e) {
        super(message, e);
    }
}
