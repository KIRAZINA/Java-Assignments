package bank;

import java.util.Random;

/**
 * TransferService (Unsafe Version)
 *
 * Demonstrates race conditions and inconsistent balances
 * because there is NO synchronization between accounts.
 */
public class TransferServiceUnsafe {
    private final Random random = new Random();

    /**
     * Transfer money between two accounts without synchronization.
     * This may cause race conditions and inconsistent balances.
     */
    public boolean transfer(BankAccount from, BankAccount to, long amount) {
        if (from == to) {
            return false; // cannot transfer to the same account
        }

        // Withdraw first
        boolean success = from.withdraw(amount);
        if (!success) {
            return false; // insufficient funds
        }

        // Deposit next
        to.deposit(amount);

        return true;
    }

    /**
     * Generate a random transfer between two accounts.
     */
    public boolean randomTransfer(BankAccount[] accounts, long maxAmount) {
        int fromIndex = random.nextInt(accounts.length);
        int toIndex = random.nextInt(accounts.length);
        long amount = 1 + random.nextInt((int) maxAmount);

        return transfer(accounts[fromIndex], accounts[toIndex], amount);
    }
}
