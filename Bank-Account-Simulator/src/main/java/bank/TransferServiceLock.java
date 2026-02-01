package bank;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * TransferService (ReentrantLock + Rollback + Rate-Limiting)
 *
 * Adds:
 * - Semaphore to limit concurrent transfers
 */
public class TransferServiceLock {

    // Allow max 5 concurrent transfers
    private final Semaphore semaphore = new Semaphore(5);

    public TransactionRecord transfer(BankAccount from, BankAccount to, long amount) {
        if (from == to) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            // Too many concurrent transfers → reject
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        BankAccount firstLock = from.getId() < to.getId() ? from : to;
        BankAccount secondLock = from.getId() < to.getId() ? to : from;

        try {
            if (firstLock.getLock().tryLock(100, TimeUnit.MILLISECONDS)) {
                try {
                    if (secondLock.getLock().tryLock(100, TimeUnit.MILLISECONDS)) {
                        try {
                            if (!from.withdraw(amount)) {
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                            }

                            try {
                                to.deposit(amount);
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.SUCCESS);
                            } catch (Exception e) {
                                from.deposit(amount); // rollback
                                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.ROLLBACK);
                            }

                        } finally {
                            secondLock.getLock().unlock();
                        }
                    } else {
                        return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                    }
                } finally {
                    firstLock.getLock().unlock();
                }
            } else {
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        } finally {
            semaphore.release();
        }
    }
}
