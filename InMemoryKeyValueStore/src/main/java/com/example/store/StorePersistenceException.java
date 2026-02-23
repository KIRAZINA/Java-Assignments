package com.example.store;

/**
 * Custom exception to handle errors related to store persistence operations.
 *
 * <p>This exception is thrown when operations such as saving to or loading from
 * a file fail due to I/O errors, invalid JSON format, or other persistence-related issues.
 *
 * <p>Example scenarios that trigger this exception:
 * <ul>
 *   <li>File cannot be written to (permission denied, disk full)</li>
 *   <li>File cannot be read (file not found, permission denied)</li>
 *   <li>JSON parsing fails due to corrupted or malformed data</li>
 *   <li>Loaded data contains invalid entries (e.g., null keys)</li>
 * </ul>
 *
 * @author Java Assignments Team
 * @version 2.0
 */
public class StorePersistenceException extends Exception {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by {@link #getMessage()})
     */
    public StorePersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by {@link #getMessage()})
     * @param cause   the cause (which is saved for later retrieval by {@link #getCause()})
     */
    public StorePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by {@link #getCause()})
     */
    public StorePersistenceException(Throwable cause) {
        super(cause);
    }
}
