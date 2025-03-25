package io.github.swampus.exception;

public class CollectionNotFoundException extends AppException {
    public CollectionNotFoundException(String name) {
        super("Collection not found: " + name);
    }
}
