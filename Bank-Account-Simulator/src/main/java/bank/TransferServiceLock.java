package bank;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
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

        BankAccount first = from.getId() < to.getId() ? from : to;
        BankAccount second = from.getId() < to.getId() ? to : from;

        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();

        try {
            if (lock1.tryLock(100, TimeUnit.MILLISECONDS)) {
                try {
                    if (lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
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
                            lock2.unlock();
                        }
                    } else {
                        return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                    }
                } finally {
                    lock1.unlock();
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
