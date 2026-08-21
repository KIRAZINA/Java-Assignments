package bank;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe collector for concurrency simulation metrics.
 *
 * <p><b>Why LongAdder instead of AtomicLong?</b></p>
 * <p>{@code LongAdder} maintains a set of internal cells that are updated
 * independently and summed on read.  Under high thread contention this
 * eliminates the "hot spot" that a single {@code AtomicLong} would create,
 * where every thread competes for the same compare-and-swap (CAS) slot.
 * The trade-off is relaxed consistency — the value observed via {@code sum()}
 * is the best-effort total at that instant — which is perfectly acceptable for
 * metrics reporting.</p>
 *
 * <p>All mutator methods are lock-free and safe for concurrent use by
 * multiple threads.</p>
 */
public class ConcurrencyMetrics {

    /* ------------------------------------------------------------------ */
    /* Counters – every field uses LongAdder for the reasons above.        */
    /* ------------------------------------------------------------------ */

    /** Total number of transfer attempts dispatched (successful or not). */
    private final LongAdder transfersAttempted = new LongAdder();

    /** Transfers that completed with {@link TransactionRecord.Status#SUCCESS}. */
    private final LongAdder transfersSuccessful = new LongAdder();

    /** Transfers that failed or were rolled back. */
    private final LongAdder transfersFailed = new LongAdder();

    /** Cumulative time (nanoseconds) spent inside {@code transfer()} calls. */
    private final LongAdder lockWaitTimeNanos = new LongAdder();

    /** Number of deadlocks observed by the deadlock monitor. */
    private final LongAdder deadlocksDetected = new LongAdder();

    /* ------------------------------------------------------------------ */
    /* Recording methods                                                  */
    /* ------------------------------------------------------------------ */

    /** Record that a transfer attempt has been initiated. */
    public void recordAttempt() {
        transfersAttempted.increment();
    }

    /**
     * Record a successful transfer.
     *
     * @param lockWaitNanos approximate time (ns) the calling thread spent
     *                       waiting for / holding locks during this transfer
     */
    public void recordSuccess(long lockWaitNanos) {
        transfersSuccessful.increment();
        lockWaitTimeNanos.add(lockWaitNanos);
    }

    /**
     * Record a failed or rolled-back transfer.
     *
     * @param lockWaitNanos approximate time (ns) spent before the failure
     */
    public void recordFailure(long lockWaitNanos) {
        transfersFailed.increment();
        lockWaitTimeNanos.add(lockWaitNanos);
    }

    /** Record that a deadlock was detected by the monitor. */
    public void recordDeadlock() {
        deadlocksDetected.increment();
    }

    /* ------------------------------------------------------------------ */
    /* Snapshot getters                                                   */
    /* ------------------------------------------------------------------ */

    public long getTransfersAttempted() {
        return transfersAttempted.sum();
    }

    public long getTransfersSuccessful() {
        return transfersSuccessful.sum();
    }

    public long getTransfersFailed() {
        return transfersFailed.sum();
    }

    /** @return total lock-wait time in nanoseconds */
    public long getLockWaitTimeNanos() {
        return lockWaitTimeNanos.sum();
    }

    public long getDeadlocksDetected() {
        return deadlocksDetected.sum();
    }

    /* ------------------------------------------------------------------ */
    /* Reporting                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Prints a beautifully formatted summary report to {@code System.out}.
     * The report is designed to be human-readable in a CI / terminal context.
     */
    public void printReport() {
        long attempted   = getTransfersAttempted();
        long successful  = getTransfersSuccessful();
        long failed      = getTransfersFailed();
        long lockWaitMs  = getLockWaitTimeNanos() / 1_000_000;
        long deadlocks   = getDeadlocksDetected();

        double successRate = attempted == 0 ? 0.0 : (double) successful / attempted * 100.0;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                  Concurrency Metrics Report                    ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Transfers Attempted : %52d ║%n", attempted));
        sb.append(String.format("║  Transfers Successful : %50d ║%n", successful));
        sb.append(String.format("║  Transfers Failed/Rolled: %49d ║%n", failed));
        sb.append(String.format("║  Success Rate          : %50.2f%% ║%n", successRate));
        sb.append(String.format("║  Lock Wait Time        : %46d ms ║%n", lockWaitMs));
        sb.append(String.format("║  Deadlocks Detected    : %52d ║%n", deadlocks));
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Note: Lock-wait time is measured as total transfer wall-time ║\n");
        sb.append("║  (includes lock acquisition + balance mutation).               ║\n");
        sb.append("║  Counters use java.util.concurrent.atomic.LongAdder.           ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════╝\n");

        System.out.print(sb);
    }

    /**
     * Resets all counters to zero.  Primarily intended for testing or
     * for re-running multiple simulations within the same JVM.
     */
    public void reset() {
        transfersAttempted.reset();
        transfersSuccessful.reset();
        transfersFailed.reset();
        lockWaitTimeNanos.reset();
        deadlocksDetected.reset();
    }
}
