package minigit.core;

/**
 * Thrown when two different byte sequences produce the same SHA-1 hash.
 * This is an extremely rare event (SHA-1 collision) and signals data corruption.
 * The HTTP layer should map this to <b>409 Conflict</b>.
 *
 * <p>Issue #9 fix: ObjectStore.store() now compares existing object bytes with
 * newly supplied bytes before silently skipping a duplicate hash. If they differ,
 * this exception is raised instead of silently discarding the new data.
 */
public class HashCollisionException extends RuntimeException {

    public HashCollisionException(String message) {
        super(message);
    }

    public HashCollisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
