package bank;

import java.util.Random;

/**
 * TransferService (Unsafe Version)
 *
 * <p>Demonstrates race conditions and inconsistent balances because there is
 * NO synchronization between accounts.  The {@code withdraw} followed by
 * {@code deposit} is a non-atomic read-modify-write sequence: if two threads
 * interleave, money can disappear or be duplicated.</p>
 *
 * <p>This class is kept deliberately unsafe so that the safe alternatives
 * ({@link TransferServiceSynchronized}, {@link TransferServiceLock}, and
 * {@link TransferServiceStampedLock}) can be compared against it in benchmarks
 * and stress tests.</p>
 */
public class TransferServiceUnsafe implements TransferService {
    private final Random random = new Random();

    /**
     * Transfer money between two accounts without synchronization.
     * This may cause race conditions and inconsistent balances.
     */
    @Override
    public TransactionRecord transfer(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Accounts cannot be null");
        }
        if (from == to) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // Withdraw first
        boolean success = from.withdraw(amount);
        if (!success) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // Deposit next
        to.deposit(amount);

        return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.SUCCESS);
    }

    /**
     * Generate a random transfer between two accounts.
     */
    public TransactionRecord randomTransfer(BankAccount[] accounts, long maxAmount) {
        if (accounts == null || accounts.length < 2) {
            throw new IllegalArgumentException("Accounts array cannot be null and must contain at least 2 accounts");
        }
        int fromIndex = random.nextInt(accounts.length);
        int toIndex = random.nextInt(accounts.length);
        long amount = 1 + random.nextInt((int) maxAmount);

        return transfer(accounts[fromIndex], accounts[toIndex], amount);
    }
}
