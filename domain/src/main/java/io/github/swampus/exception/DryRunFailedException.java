package io.github.swampus.exception;

public class DryRunFailedException extends AppException {

    public DryRunFailedException(String message) {
        super(message);
    }

    public DryRunFailedException(String message, Exception e) {
        super(message, e);
    }
}
