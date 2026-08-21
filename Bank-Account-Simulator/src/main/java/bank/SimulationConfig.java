package bank;

import java.time.Duration;

/**
 * Immutable, thread-safe configuration for a bank-account transfer simulation.
 *
 * <p>Implemented as a Java 17 {@code record} — instances are inherently
 * immutable and therefore safe to share across threads without additional
 * synchronization.</p>
 *
 * <p>The record captures every parameter that the {@link SimulationEngine}
 * needs to set up a simulation run, replacing the parameter-list explosion
 * that previously existed across the fragmented {@code SimulationRunner*}
 * classes.</p>
 *
 * @param numAccounts           how many BankAccount objects to create
 * @param initialBalance        starting balance for every account
 * @param numThreads            size of the worker thread pool
 * @param numTransfers          total number of transfer operations to execute
 * @param minTransferAmount     smallest random transfer amount (inclusive)
 * @param maxTransferAmount     largest random transfer amount (inclusive)
 * @param duration              overall simulation duration (used for timeout
 *                              and deadlock-monitor scheduling)
 * @param enableDeadlockMonitor whether a background ThreadMXBean monitor runs
 *                              periodically to detect deadlocks
 * @param useBarrier            whether worker threads synchronise on a
 *                              {@link java.util.concurrent.CyclicBarrier} before
 *                              starting their first transfer (wave-based execution)
 * @param enableHistory         whether every TransactionRecord is stored in an
 *                              in-memory queue for post-simulation inspection
 */
public record SimulationConfig(
        int numAccounts,
        long initialBalance,
        int numThreads,
        int numTransfers,
        int minTransferAmount,
        int maxTransferAmount,
        Duration duration,
        boolean enableDeadlockMonitor,
        boolean useBarrier,
        boolean enableHistory
) {

    /**
     * Compact canonical constructor with validation.
     * Validation runs automatically whenever a {@code SimulationConfig} is
     * created, failing fast on invalid parameters.
     */
    public SimulationConfig {
        if (numAccounts < 2) {
            throw new IllegalArgumentException("numAccounts must be >= 2, got " + numAccounts);
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("initialBalance must be >= 0, got " + initialBalance);
        }
        if (numThreads < 1) {
            throw new IllegalArgumentException("numThreads must be >= 1, got " + numThreads);
        }
        if (numTransfers < 0) {
            throw new IllegalArgumentException("numTransfers must be >= 0, got " + numTransfers);
        }
        if (minTransferAmount <= 0) {
            throw new IllegalArgumentException("minTransferAmount must be positive, got " + minTransferAmount);
        }
        if (maxTransferAmount < minTransferAmount) {
            throw new IllegalArgumentException(
                    "maxTransferAmount (" + maxTransferAmount + ") must be >= minTransferAmount (" + minTransferAmount + ")");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Convenience factory methods                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Build a config with the most commonly used defaults.
     */
    public static SimulationConfig defaults() {
        return new SimulationConfig(
                10,           // 10 accounts
                1000L,        // $1000 each
                8,            // 8 threads
                10_000,       // 10k transfers
                1,            // min $1
                50,           // max $50
                Duration.ofSeconds(30),
                false,        // no deadlock monitor
                false,        // no barrier
                false         // no history
        );
    }

    /**
     * Fluent builder for fine-grained control.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int numAccounts = 10;
        private long initialBalance = 1000L;
        private int numThreads = 8;
        private int numTransfers = 10_000;
        private int minTransferAmount = 1;
        private int maxTransferAmount = 50;
        private Duration duration = Duration.ofSeconds(30);
        private boolean enableDeadlockMonitor = false;
        private boolean useBarrier = false;
        private boolean enableHistory = false;

        public Builder numAccounts(int n)            { this.numAccounts = n; return this; }
        public Builder initialBalance(long b)        { this.initialBalance = b; return this; }
        public Builder numThreads(int n)             { this.numThreads = n; return this; }
        public Builder numTransfers(int n)           { this.numTransfers = n; return this; }
        public Builder minTransferAmount(int m)      { this.minTransferAmount = m; return this; }
        public Builder maxTransferAmount(int m)      { this.maxTransferAmount = m; return this; }
        public Builder duration(Duration d)          { this.duration = d; return this; }
        public Builder enableDeadlockMonitor(boolean b) { this.enableDeadlockMonitor = b; return this; }
        public Builder useBarrier(boolean b)          { this.useBarrier = b; return this; }
        public Builder enableHistory(boolean b)       { this.enableHistory = b; return this; }

        public SimulationConfig build() {
            return new SimulationConfig(
                    numAccounts, initialBalance, numThreads, numTransfers,
                    minTransferAmount, maxTransferAmount, duration,
                    enableDeadlockMonitor, useBarrier, enableHistory);
        }
    }
}
