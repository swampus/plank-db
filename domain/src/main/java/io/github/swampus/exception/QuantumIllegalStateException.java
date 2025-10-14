package io.github.swampus.exception;

public class QuantumIllegalStateException extends AppException {
    public QuantumIllegalStateException(String message) {
        super(message);
    }

    public QuantumIllegalStateException(String message, Exception e) {
        super(message, e);
    }
}
