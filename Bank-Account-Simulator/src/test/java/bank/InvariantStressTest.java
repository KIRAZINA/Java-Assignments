package bank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Stress tests that verify the fundamental invariant of a banking system:
 * money must be conserved.  The sum of all account balances before a batch
 * of concurrent transfers must equal the sum after — regardless of how many
 * transfers succeed, fail, or are rolled back.
 *
 * <p>This property is tested against two service implementations that use
 * different locking strategies: {@link TransferServiceLock} (ReentrantLock)
 * and {@link TransferServiceStampedLock} (StampedLock with optimistic reads).</p>
 *
 * <p>All thread pools are shut down in {@code finally} blocks to prevent
 * test hangs in CI environments.</p>
 */
@DisplayName("Invariant Stress Tests")
class InvariantStressTest {

    private static final int NUM_ACCOUNTS = 100;
    private static final long INITIAL_BALANCE = 1000L;
    private static final int NUM_THREADS = 20;
    private static final int TRANSFERS_PER_THREAD = 2500; // 20 * 2500 = 50,000 total
    private static final long TOTAL_INITIAL = (long) NUM_ACCOUNTS * INITIAL_BALANCE;

    /**
     * Creates 100 accounts each starting with $1000.
     */
    private BankAccount[] createAccounts() {
        BankAccount[] accounts = new BankAccount[NUM_ACCOUNTS];
        for (int i = 0; i < NUM_ACCOUNTS; i++) {
            accounts[i] = new BankAccount(i, INITIAL_BALANCE);
        }
        return accounts;
    }

    /**
     * Computes the sum of all account balances.
     */
    private long totalBalance(BankAccount[] accounts) {
        long sum = 0L;
        for (BankAccount account : accounts) {
            sum += account.getBalance();
        }
        return sum;
    }

    /**
     * Runs 50,000 concurrent transfers using the given service and asserts
     * that the total balance is conserved — i.e. money is neither created
     * nor destroyed by the concurrent operations.
     *
     * @param service the transfer service to stress-test
     */
    private void runInvariantTest(TransferService service, String serviceName) throws InterruptedException {
        BankAccount[] accounts = createAccounts();
        long sumBefore = totalBalance(accounts);

        // Sanity: the initial sum must be exactly what we expect
        assertThat(sumBefore).isEqualTo(TOTAL_INITIAL);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        // Use ConcurrentLinkedQueue to collect exceptions from worker threads
        var exceptions = new java.util.concurrent.ConcurrentLinkedQueue<Exception>();

        try {
            for (int t = 0; t < NUM_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                            int fromIdx = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            int toIdx = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);

                            if (fromIdx == toIdx) {
                                continue;
                            }

                            long amount = 1 + ThreadLocalRandom.current().nextLong(100);

                            TransactionRecord record = service.transfer(
                                    accounts[fromIdx], accounts[toIdx], amount);

                            // Every record must have a valid status
                            assertThat(record.getStatus()).isNotNull();
                        }
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait up to 60 seconds for all workers to finish
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            assertThat(completed)
                    .as("Simulation should complete within 60 seconds for " + serviceName)
                    .isTrue();

        } finally {
            // Always shut down the executor to prevent test hangs
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        long sumAfter = totalBalance(accounts);

        // No exceptions should have escaped from worker threads
        assertThat(exceptions).as("No exceptions should occur during " + serviceName + " transfers")
                .isEmpty();

        // THE KEY INVARIANT: sum of all balances must be unchanged
        assertThat(sumAfter)
                .as("Balance conservation for %s: before=%d, after=%d", serviceName, sumBefore, sumAfter)
                .isEqualTo(sumBefore);

        System.out.printf("[%s] Sum before=%d, sum after=%d, transfers=%d, conservation=%s%n",
                serviceName, sumBefore, sumAfter,
                NUM_THREADS * TRANSFERS_PER_THREAD,
                sumAfter == sumBefore ? "PASS" : "FAIL");
    }

    @Test
    @DisplayName("ReentrantLock service must conserve total balance under 50k concurrent transfers")
    void reentrantLockInvariant50kTransfers() throws InterruptedException {
        runInvariantTest(new TransferServiceLock(), "ReentrantLock");
    }

    @Test
    @DisplayName("StampedLock service must conserve total balance under 50k concurrent transfers")
    void stampedLockInvariant50kTransfers() throws InterruptedException {
        runInvariantTest(new TransferServiceStampedLock(), "StampedLock");
    }

    @Test
    @DisplayName("Synchronized service must conserve total balance under 50k concurrent transfers")
    void synchronizedInvariant50kTransfers() throws InterruptedException {
        runInvariantTest(new TransferServiceSynchronized(), "Synchronized");
    }
}
