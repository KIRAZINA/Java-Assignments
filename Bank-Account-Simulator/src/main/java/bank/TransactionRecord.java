package bank;

/**
 * Immutable transaction record capturing the outcome of a single fund transfer.
 *
 * <p>Implemented as a Java 17 {@code record} for concise, immutable data
 * carriers.  A convenience canonical constructor (timestamp auto-generated) is
 * provided for callers that do not need to specify the timestamp manually.</p>
 */
public record TransactionRecord(int fromId, int toId, long amount, Status status, long timestamp) {

    /**
     * The lifecycle state of a transfer attempt.
     */
    public enum Status {
        SUCCESS,
        FAILED,
        ROLLBACK
    }

    /**
     * Creates a record with the current wall-clock time as the timestamp.
     */
    public TransactionRecord(int fromId, int toId, long amount, Status status) {
        this(fromId, toId, amount, status, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // Backward-compatible getters (existing code/tests use getXxx() not xxx())
    // ------------------------------------------------------------------

    public int getFromId() {
        return fromId;
    }

    public int getToId() {
        return toId;
    }

    public long getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Transaction[from=%d, to=%d, amount=%d, status=%s, time=%d]",
                fromId, toId, amount, status, timestamp);
    }
}
