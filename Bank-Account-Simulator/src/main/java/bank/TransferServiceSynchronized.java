package bank;

/**
 * TransferService (Safe Version with synchronized)
 *
 * Ensures atomic transfers by synchronizing on both accounts.
 * WARNING:
 * - Deadlocks may occur if two threads lock accounts in opposite order.
 * - We'll later fix this using lock ordering or ReentrantLock.
 */
public class TransferServiceSynchronized {

    /**
     * Transfer money between two accounts using synchronized blocks.
     * This ensures atomicity but may cause deadlocks.
     */
    public boolean transfer(BankAccount from, BankAccount to, long amount) {
        if (from == to) {
            return false;
        }

        // Synchronize on both accounts
        synchronized (from) {
            synchronized (to) {
                if (!from.withdraw(amount)) {
                    return false; // insufficient funds
                }
                to.deposit(amount);
                return true;
            }
        }
    }
}
