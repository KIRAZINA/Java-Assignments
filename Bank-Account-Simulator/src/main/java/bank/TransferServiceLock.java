package bank;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * TransferService (ReentrantLock + Rollback + Rate-Limiting)
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Semaphore to limit concurrent transfers (rate limiting / backpressure)</li>
 *   <li>Lock ordering to prevent deadlocks (lower account ID first)</li>
 *   <li>Timeout-based locking with {@code tryLock} to detect potential deadlock
 *       conditions and fail fast rather than blocking forever</li>
 *   <li>Automatic rollback on deposit failure (e.g. overflow)</li>
 * </ul>
 *
 * <p><b>Lock ordering enforcement and deadlock prevention</b>:
 * The service always acquires the {@link java.util.concurrent.locks.ReentrantLock}
 * for the account with the <em>lower</em> ID first, then the higher-ID account.
 * This "resource hierarchy" technique mathematically satisfies the deadlock-freedom
 * proof: it breaks the <i>circular wait</i> condition (one of the four Coffman
 * conditions) by imposing a total ordering on lock acquisition.  If every thread
 * acquires locks in the same global order, a wait-for cycle can never form.
 *
 * Additionally, {@code tryLock} with a timeout is used for the second lock.  If
 * the second lock cannot be acquired within the timeout, the first lock is
 * released and the transfer fails immediately.  This provides an extra safety
 * net against any unforeseen contention scenario that could lead to a livelock
 * or near-deadlock state.</p>
 *
 * <p>Design choice: why not use a global lock?  A single global lock would be
 * deadlock-free but would serialise all transfers, eliminating concurrency.
 * Lock ordering with per-account locks allows independent transfers to proceed
 * in parallel while still guaranteeing deadlock-freedom.</p>
 */
public class TransferServiceLock implements TransferService {

    // Constants for configuration
    private static final int MAX_CONCURRENT_TRANSFERS = 5;
    private static final long LOCK_TIMEOUT_MS = 100;

    // Allow max concurrent transfers – acts as a throttling mechanism
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TRANSFERS);

    @Override
    public TransactionRecord transfer(BankAccount from, BankAccount to, long amount) {
        // Null checks
        if (from == null || to == null) {
            throw new IllegalArgumentException("Accounts cannot be null");
        }

        // Cannot transfer to same account
        if (from == to) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // Try to acquire semaphore permit
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire();
            if (!acquired) {
                // Too many concurrent transfers – reject (backpressure)
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
            }

            // Lock ordering to prevent deadlocks: always lock account with smaller ID first
            BankAccount firstLock = from.getId() < to.getId() ? from : to;
            BankAccount secondLock = from.getId() < to.getId() ? to : from;

            // Try to acquire first lock
            if (firstLock.getLock().tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                try {
                    // Try to acquire second lock
                    if (secondLock.getLock().tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        try {
                            // Attempt withdrawal
                            if (!from.withdraw(amount)) {
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                            }

                            // Attempt deposit with rollback on failure
                            try {
                                to.deposit(amount);
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.SUCCESS);
                            } catch (Exception e) {
                                // Rollback: return money to source account
                                from.deposit(amount);
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.ROLLBACK);
                            }

                        } finally {
                            secondLock.getLock().unlock();
                        }
                    } else {
                        // Failed to acquire second lock
                        return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                    }
                } finally {
                    firstLock.getLock().unlock();
                }
            } else {
                // Failed to acquire first lock
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        } finally {
            // Only release if we actually acquired the permit
            if (acquired) {
                semaphore.release();
            }
        }
    }
}
