package bank;

/**
 * TransferService (Safe Version with synchronized)
 *
 * Ensures atomic transfers by synchronizing on both accounts.
 * FIXED:
 * - Added lock ordering to prevent deadlocks
 * - Added null checks for robustness
 */
public class TransferServiceSynchronized {

    /**
     * Transfer money between two accounts using synchronized blocks.
     * This ensures atomicity and prevents deadlocks through lock ordering.
     */
    public boolean transfer(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Accounts cannot be null");
        }
        if (from == to) {
            return false;
        }

        // Lock ordering to prevent deadlocks: always lock account with smaller ID first
        BankAccount first = from.getId() < to.getId() ? from : to;
        BankAccount second = from.getId() < to.getId() ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (!from.withdraw(amount)) {
                    return false; // insufficient funds
                }
                to.deposit(amount);
                return true;
            }
        }
    }
}
