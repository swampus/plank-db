package io.github.swampus.exception;

public class QuantumNoSuchElementException extends AppException {
    public QuantumNoSuchElementException(String message) {
        super(message);
    }

    public QuantumNoSuchElementException(String message, Exception e) {
        super(message, e);
    }
}
