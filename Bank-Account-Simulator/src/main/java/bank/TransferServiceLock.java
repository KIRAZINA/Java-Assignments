package bank;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * TransferService (ReentrantLock + Rollback + Rate-Limiting)
 *
 * Features:
 * - Semaphore to limit concurrent transfers
 * - Lock ordering to prevent deadlocks
 * - Timeout-based locking with tryLock
 * - Automatic rollback on deposit failure
 */
public class TransferServiceLock {

    // Constants for configuration
    private static final int MAX_CONCURRENT_TRANSFERS = 5;
    private static final long LOCK_TIMEOUT_MS = 100;

    // Allow max concurrent transfers
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TRANSFERS);

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
                // Too many concurrent transfers - reject
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
