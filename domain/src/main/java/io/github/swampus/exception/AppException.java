package io.github.swampus.exception;

public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Exception e) {
        super(message, e);
    }
}
