package bank;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Represents a bank account with thread-safe balance operations.
 *
 * <p>The account exposes three independent locking mechanisms:</p>
 * <ul>
 *   <li>{@link #getLock()} – a {@link ReentrantLock} used by {@link TransferServiceLock}</li>
 *   <li>{@link #getStampedLock()} – a {@link StampedLock} used by {@link TransferServiceStampedLock}</li>
 *   <li>intrinsic monitor – used by {@link TransferServiceSynchronized}</li>
 * </ul>
 *
 * <p>The balance is stored in an {@link AtomicLong} because individual
 * {@code deposit}/{@code withdraw} operations use CAS loops for atomic
 * overflow checks.  However, {@code AtomicLong} alone is <strong>not</strong>
 * sufficient for multi-account transfers – the caller must hold an
 * appropriate lock around the pair of operations to guarantee atomicity.</p>
 */
public class BankAccount {
    private final int id;
    private final AtomicLong balance; // AtomicLong for monitoring, but not enough alone for transfers
    private final ReentrantLock lock = new ReentrantLock();

    /*
     * StampedLock provides three modes of access:
     *   - write lock  (exclusive, blocking)   → writeLock() / unlockWrite(stamp)
     *   - read lock   (shared, blocking)      → readLock() / unlockRead(stamp)
     *   - optimistic read (lock-free, validate) → tryOptimisticRead() / validate(stamp)
     *
     * We expose it here so that TransferServiceStampedLock can perform
     * optimistic reads and upgrade to write locks on the same account instance.
     */
    private final StampedLock stampedLock = new StampedLock();

    public BankAccount(int id, long initialBalance) {
        this.id = id;
        this.balance = new AtomicLong(initialBalance);
    }

    public int getId() {
        return id;
    }

    public long getBalance() {
        return balance.get();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    /**
     * Returns the {@link StampedLock} associated with this account.
     *
     * <p>This lock is used by {@link TransferServiceStampedLock} to perform
     * optimistic reads and write-lock upgrades in a single, unified locking
     * strategy.</p>
     */
    public StampedLock getStampedLock() {
        return stampedLock;
    }

    /**
     * Deposit money into the account.
     * Thread-safe with atomic overflow check using CAS loop.
     */
    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        // Use CAS loop to ensure atomic overflow check and update
        long current;
        long newBalance;
        do {
            current = balance.get();
            // Check for overflow before attempting update
            if (current > Long.MAX_VALUE - amount) {
                throw new ArithmeticException("Balance overflow detected");
            }
            newBalance = current + amount;
        } while (!balance.compareAndSet(current, newBalance));
    }

    /**
     * Withdraw money from the account.
     * Must ensure balance never goes negative.
     */
    public boolean withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }

        long current;
        do {
            current = balance.get();
            if (current < amount) {
                return false; // insufficient funds
            }
        } while (!balance.compareAndSet(current, current - amount));

        return true;
    }

    @Override
    public String toString() {
        return "BankAccount{id=" + id + ", balance=" + balance.get() + "}";
    }
}
