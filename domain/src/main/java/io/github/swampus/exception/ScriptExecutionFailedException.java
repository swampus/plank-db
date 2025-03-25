package io.github.swampus.exception;

public class ScriptExecutionFailedException extends AppException {
    public ScriptExecutionFailedException(String message) {
        super(message);
    }

    public ScriptExecutionFailedException(String message, Exception e) {
        super(message, e);
    }
}
