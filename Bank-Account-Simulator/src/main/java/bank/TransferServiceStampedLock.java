package bank;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

/**
 * TransferService backed by {@link java.util.concurrent.locks.StampedLock}.
 *
 * <p><b>Optimistic reading pattern</b>:</p>
 * <p>StampedLock offers three access modes: write lock (exclusive), read lock
 * (shared, blocking), and optimistic read (lock-free, validated).</p>
 *
 * <p>The optimistic-read workflow is:</p>
 * <ol>
 *   <li>Call {@code tryOptimisticRead()} on each account's StampedLock to
 *       obtain a "version stamp" — this is virtually free (no blocking).</li>
 *   <li>Read the balance fields without holding any lock.</li>
 *   <li>Call {@code validate(stamp)} on each stamp.  If validation succeeds,
 *       the data was not modified by a writer between the optimistic read and
 *       the validation call, so the values we read are still trustworthy.</li>
 *   <li>If validation fails (a writer changed the data in the interim), the
 *       optimistic result is discarded and we fall back to a pessimistic
 *       write-lock path that re-reads the balance.</li>
 * </ol>
 *
 * <p>In a transfer scenario we always end up writing, so the optimistic read
 * serves as a fast "sanity check" / rejection path: if the optimistic read
 * shows insufficient funds and the stamps still validate, we can return
 * {@code FAILED} immediately without ever acquiring a write lock — saving the
 * overhead of lock acquisition for the common "not enough money" case.</p>
 *
 * <p><b>Lock ordering</b>:</p>
 * <p>Write locks are always acquired on the account with the lower
 * {@code id} first.  This breaks the circular-wait condition and
 * mathematically prevents deadlocks, exactly as in {@link TransferServiceLock}
 * and {@link TransferServiceSynchronized}.</p>
 *
 * <p><b>Why not use StampedLock.asWriteLock() / asReadLock()?</b></p>
 * <p>Those convenience adapters convert a StampedLock into a traditional
 * {@link java.util.concurrent.locks.Lock}.  We avoid them because the native
 * stamp-based API gives us finer control — e.g. tryOptimisticRead() has no
 * Lock equivalent and is the central feature of this service.</p>
 */
public class TransferServiceStampedLock implements TransferService {

    /** Timeout for the second (dependent) write lock — safety-net against contention stalls. */
    private static final long LOCK_TIMEOUT_MS = 200;

    @Override
    public TransactionRecord transfer(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Accounts cannot be null");
        }
        if (from == to) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // ------------------------------------------------------------------
        // Lock ordering: always acquire the write lock on the account with
        // the smaller ID first.  This enforces a global total order on lock
        // acquisition, making a circular wait (and therefore a deadlock)
        // impossible.
        // ------------------------------------------------------------------
        BankAccount first  = from.getId() < to.getId() ? from : to;
        BankAccount second = from.getId() < to.getId() ? to : from;

        StampedLock firstLock  = first.getStampedLock();
        StampedLock secondLock = second.getStampedLock();

        // ------------------------------------------------------------------
        // Phase 1 — Optimistic read.
        //
        // tryOptimisticRead() returns a stamp.  No lock is actually held;
        // the thread just records the current "version" of the data.  If a
        // writer modifies the account between here and the validate() call,
        // validate() returns false and we know our read was stale.
        // ------------------------------------------------------------------
        long stamp1 = firstLock.tryOptimisticRead();
        long stamp2 = secondLock.tryOptimisticRead();

        // Read balances optimistically — no monitor or lock is held.
        long fromBalance = from.getBalance();

        // Validate: were either account modified by a writer since the
        // optimistic read?  If the stamps are still valid, the balances we
        // just read are accurate at this instant.
        boolean stampsValid = firstLock.validate(stamp1) && secondLock.validate(stamp2);

        // Fast path: optimistic read is valid AND shows insufficient funds.
        // We can return immediately without acquiring any write lock — the
        // cheapest possible outcome for the common "no money" case.
        if (stampsValid && fromBalance < amount) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // ------------------------------------------------------------------
        // Phase 2 — Upgrade to write locks.
        //
        // Even if the optimistic read said "sufficient funds", we must acquire
        // exclusive write locks to safely mutate both balances.  The balance
        // might have changed between the optimistic read and here.
        // ------------------------------------------------------------------
        long wStamp1 = firstLock.writeLock();   // blocking — safe due to lock ordering
        try {
            long wStamp2;
            try {
                wStamp2 = secondLock.tryWriteLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
            }

            if (wStamp2 == 0L) {
                // Could not acquire the second write lock within the timeout.
                // Release the first lock and report failure (avoids livelock).
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
            }

            try {
                // ------------------------------------------------------------------
                // CRITICAL SECTION — double-checked locking.
                //
                // Re-read the balance under the exclusive write locks.  The
                // optimistic read in Phase 1 might have been stale, or another
                // thread might have withdrawn funds between the optimistic read
                // and our write-lock acquisition.  We MUST re-check here.
                // ------------------------------------------------------------------
                if (from.getBalance() < amount) {
                    return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                }

                // Perform the actual transfer.
                if (!from.withdraw(amount)) {
                    return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                }

                try {
                    to.deposit(amount);
                    return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.SUCCESS);
                } catch (ArithmeticException e) {
                    // Deposit failed (e.g. balance overflow).  Roll back by
                    // returning the withdrawn funds to the source account.
                    from.deposit(amount);
                    return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.ROLLBACK);
                }
            } finally {
                secondLock.unlockWrite(wStamp2);
            }
        } finally {
            firstLock.unlockWrite(wStamp1);
        }
    }
}
