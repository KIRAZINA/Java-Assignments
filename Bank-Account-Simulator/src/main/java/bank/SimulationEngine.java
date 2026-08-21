package bank;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Unified simulation engine for concurrent bank-transfer experiments.
 *
 * <p>This single class replaces the six fragmented {@code SimulationRunner*}
 * classes that previously existed.  It accepts a {@link TransferService} and
 * an immutable {@link SimulationConfig}, then orchestrates the full lifecycle
 * of a simulation run:</p>
 *
 * <ol>
 *   <li>Account creation (based on config parameters)</li>
 *   <li>Thread-pool creation and task submission</li>
 *   <li>Optional CyclicBarrier synchronisation (wave-based execution)</li>
 *   <li>Optional transaction-history collection</li>
 *   <li>Optional background deadlock monitoring via {@link ThreadMXBean}</li>
 *   <li>Graceful shutdown of all executors</li>
 *   <li>Metrics summary report printing</li>
 * </ol>
 *
 * <p>All executors are shut down in {@code finally} blocks to guarantee
 * resource cleanup even when exceptions occur or the simulation is
 * interrupted.</p>
 */
public class SimulationEngine {

    private final TransferService transferService;
    private final SimulationConfig config;
    private final ConcurrencyMetrics metrics;

    /**
     * Creates a new engine.
     *
     * @param transferService the transfer strategy (Unsafe, Synchronized, Lock, StampedLock)
     * @param config          immutable simulation parameters
     */
    public SimulationEngine(TransferService transferService, SimulationConfig config) {
        this.transferService = transferService;
        this.config = config;
        this.metrics = new ConcurrencyMetrics();
    }

    /** @return the metrics collected during the last (or current) run. */
    public ConcurrencyMetrics getMetrics() {
        return metrics;
    }

    /**
     * Runs the simulation end-to-end and prints results to the console.
     *
     * @return a {@link SimulationResult} capturing before/after balances and metrics
     * @throws InterruptedException if the simulation is interrupted while waiting
     */
    public SimulationResult run() throws InterruptedException {
        // ------------------------------------------------------------------
        // 1. Initialise accounts
        // ------------------------------------------------------------------
        BankAccount[] accounts = initAccounts(config.numAccounts(), config.initialBalance());
        long totalBefore = totalBalance(accounts);

        System.out.println("Starting simulation: " + config.numTransfers() + " transfers, "
                + config.numThreads() + " threads, " + config.numAccounts() + " accounts");
        System.out.println("Total balance before: " + totalBefore);

        // ------------------------------------------------------------------
        // 2. Set up concurrency primitives based on config flags
        // ------------------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(config.numThreads());

        // Optional barrier — synchronises thread start for wave-based execution
        CyclicBarrier barrier = config.useBarrier()
                ? new CyclicBarrier(config.numThreads())
                : null;

        // Optional history collection — thread-safe queue
        ConcurrentLinkedQueue<TransactionRecord> history = config.enableHistory()
                ? new ConcurrentLinkedQueue<>()
                : null;

        // Optional deadlock monitor — scheduled task that polls ThreadMXBean
        ScheduledExecutorService scheduler = null;
        ScheduledFuture<?> monitorHandle = null;
        if (config.enableDeadlockMonitor()) {
            scheduler = Executors.newScheduledThreadPool(1);
            // Check every 2 seconds
            monitorHandle = scheduler.scheduleAtFixedRate(
                    this::checkDeadlocks, 0, 2, TimeUnit.SECONDS);
        }

        // ------------------------------------------------------------------
        // 3. Submit transfer tasks
        // ------------------------------------------------------------------
        CountDownLatch latch = new CountDownLatch(config.numTransfers());

        for (int i = 0; i < config.numTransfers(); i++) {
            executor.submit(() -> {
                try {
                    // Barrier: wait for all threads in a wave to be ready
                    if (barrier != null) {
                        barrier.await(config.duration().toMillis(), TimeUnit.MILLISECONDS);
                    }

                    // Pick two distinct random accounts and a random amount
                    int fromIndex;
                    int toIndex;
                    do {
                        fromIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                        toIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                    } while (fromIndex == toIndex);

                    int range = config.maxTransferAmount() - config.minTransferAmount() + 1;
                    long amount = config.minTransferAmount()
                            + ThreadLocalRandom.current().nextInt(range);

                    // Record timing as a proxy for lock-wait time
                    metrics.recordAttempt();
                    long start = System.nanoTime();
                    TransactionRecord record;
                    try {
                        record = transferService.transfer(accounts[fromIndex], accounts[toIndex], amount);
                    } catch (Exception e) {
                        record = new TransactionRecord(
                                accounts[fromIndex].getId(), accounts[toIndex].getId(),
                                amount, TransactionRecord.Status.FAILED);
                    }
                    long elapsed = System.nanoTime() - start;

                    if (record.getStatus() == TransactionRecord.Status.SUCCESS) {
                        metrics.recordSuccess(elapsed);
                    } else {
                        metrics.recordFailure(elapsed);
                    }

                    if (history != null) {
                        history.add(record);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (TimeoutException e) {
                    System.err.println("Barrier wait timed out: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error in transfer task: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // ------------------------------------------------------------------
        // 4. Wait for completion (with duration-derived timeout)
        // ------------------------------------------------------------------
        try {
            // Use the configured duration as a hard timeout
            boolean completed = latch.await(config.duration().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                System.err.println("WARNING: Simulation did not complete within "
                        + config.duration().toSeconds() + " seconds. Forcing shutdown.");
            }
        } finally {
            // ------------------------------------------------------------------
            // 5. Graceful shutdown of all executors
            // ------------------------------------------------------------------
            try {
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            } finally {
                if (scheduler != null) {
                    if (monitorHandle != null) {
                        monitorHandle.cancel(false);
                    }
                    scheduler.shutdown();
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 6. Collect results and print report
        // ------------------------------------------------------------------
        long totalAfter = totalBalance(accounts);
        System.out.println("Total balance after:  " + totalAfter);
        System.out.println("Conservation check:   "
                + (totalBefore == totalAfter ? "PASS ✓" : "FAIL ✗"));

        if (history != null && !history.isEmpty()) {
            System.out.println("\nLast 10 transactions:");
            history.stream()
                    .skip(Math.max(0, history.size() - 10))
                    .forEach(System.out::println);
        }

        metrics.printReport();

        return new SimulationResult(totalBefore, totalAfter, metrics, history);
    }

    // ------------------------------------------------------------------ //
    //  Deadlock monitoring                                                //
    // ------------------------------------------------------------------ //

    /**
     * Polls the {@link ThreadMXBean} for deadlocked threads.  If any are
     * found, records the event in metrics and prints a warning.
     *
     * <p>{@code findDeadlockedThreads()} detects both object-monitor
     * deadlocks (classic {@code synchronized} cycles) and
     * ownable-synchronizer deadlocks (e.g. {@code ReentrantLock} cycles).</p>
     */
    private void checkDeadlocks() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        if (deadlockedThreads != null) {
            metrics.recordDeadlock();
            System.err.println("DEADLOCK DETECTED! Thread IDs: " + Arrays.toString(deadlockedThreads));

            // Attempt graceful interruption of deadlocked threads
            for (long threadId : deadlockedThreads) {
                Thread thread = threadById(threadId);
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }

    /** Tiny helper to look up a live Thread by its system ID. */
    private static Thread threadById(long threadId) {
        // In production you'd use Thread.getAllStackTraces().keySet()
        // or a ThreadMXBean.getThreadInfo() call.  Here we scan for simplicity.
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getId() == threadId) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Helper methods                                                     //
    // ------------------------------------------------------------------ //

    private static BankAccount[] initAccounts(int numAccounts, long initialBalance) {
        BankAccount[] accounts = new BankAccount[numAccounts];
        for (int i = 0; i < numAccounts; i++) {
            accounts[i] = new BankAccount(i, initialBalance);
        }
        return accounts;
    }

    private static long totalBalance(BankAccount[] accounts) {
        return Arrays.stream(accounts).mapToLong(BankAccount::getBalance).sum();
    }

    /**
     * Immutable snapshot of a simulation run's results.
     */
    public record SimulationResult(
            long totalBefore,
            long totalAfter,
            ConcurrencyMetrics metrics,
            ConcurrentLinkedQueue<TransactionRecord> history
    ) {
        public boolean isConservationHeld() {
            return totalBefore == totalAfter;
        }
    }

    /**
     * Convenience entry point — runs a simulation from the command line.
     *
     * Usage:
     *   java -cp ... bank.SimulationEngine
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(String[] args) throws InterruptedException {
        TransferService service = new TransferServiceLock();
        SimulationConfig config = SimulationConfig.builder()
                .numAccounts(20)
                .initialBalance(1000L)
                .numThreads(8)
                .numTransfers(50_000)
                .minTransferAmount(1)
                .maxTransferAmount(100)
                .enableDeadlockMonitor(true)
                .useBarrier(false)
                .enableHistory(true)
                .build();

        SimulationEngine engine = new SimulationEngine(service, config);
        SimulationResult result = engine.run();

        if (!result.isConservationHeld()) {
            System.err.println("WARNING: Balance conservation violated!");
            System.exit(1);
        }
    }
}
