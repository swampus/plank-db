package io.github.swampus.exception;

public class QuantumInvalidInputException extends AppException {
    public QuantumInvalidInputException(String message) {
        super(message);
    }

    public QuantumInvalidInputException(String message, Exception e) {
        super(message, e);
    }
}
