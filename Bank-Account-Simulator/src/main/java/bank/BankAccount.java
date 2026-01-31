package bank;

import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * Deposit money into the account.
     * Safe version will synchronize to ensure atomicity.
     */
    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        balance.addAndGet(amount);
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
