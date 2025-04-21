package io.github.swampus.exception;

public class SerializeKeyException extends AppException {
    public SerializeKeyException(String message) {
        super(message);
    }

    public SerializeKeyException(String message, Exception e) {
        super(message, e);
    }
}
