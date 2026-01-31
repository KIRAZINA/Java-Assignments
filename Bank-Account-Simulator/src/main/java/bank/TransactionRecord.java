package bank;

/**
 * Represents a single transfer transaction record.
 */
public class TransactionRecord {
    private final int fromId;
    private final int toId;
    private final long amount;
    private final Status status;
    private final long timestamp;

    public enum Status {
        SUCCESS,
        FAILED,
        ROLLBACK
    }

    public TransactionRecord(int fromId, int toId, long amount, Status status) {
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    // ✅ Getter for status
    public Status getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("Transaction[from=%d, to=%d, amount=%d, status=%s, time=%d]",
                fromId, toId, amount, status, timestamp);
    }
}
