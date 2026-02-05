package bank;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a bank account with thread-safe balance operations.
 *
 * NOTE:
 * - In the unsafe version, we will remove synchronization to demonstrate race conditions.
 * - In the safe version, we will use synchronized/locks.
 */
public class BankAccount {
    private final int id;
    private final AtomicLong balance; // AtomicLong for monitoring, but not enough alone for transfers
    private final ReentrantLock lock = new ReentrantLock();

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
