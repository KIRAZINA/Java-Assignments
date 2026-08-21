package app.service;

/**
 * Thrown when an operation references a document ID that is not present in the
 * index. Mapped to HTTP {@code 404 Not Found} by {@link app.controller.GlobalExceptionHandler}.
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}
