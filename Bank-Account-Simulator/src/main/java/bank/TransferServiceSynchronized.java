package bank;

/**
 * TransferService (Safe Version with {@code synchronized})
 *
 * <p>Ensures atomic fund transfers by synchronizing on both accounts using
 * Java's intrinsic monitors.  The return type is {@link TransactionRecord}
 * so that the {@link SimulationEngine} can collect metrics and maintain an
 * audit trail.</p>
 *
 * <p><b>Dread-lock prevention via lock ordering</b>: the account with the
 * lower {@code id} is always locked first.  This breaks the <i>circular wait</i>
 * condition required for a deadlock, providing a mathematical guarantee that
 * no deadlock can occur as long as every transfer follows the same global
 * ordering.</p>
 */
public class TransferServiceSynchronized implements TransferService {

    /**
     * Transfer money between two accounts using synchronized blocks.
     * <p>
     * Lock ordering rationale:
     * In a multi-account system, a deadlock occurs when Thread-A holds lock-1
     * and waits for lock-2 while Thread-B holds lock-2 and waits for lock-1.
     * By always acquiring locks in a consistent order (lower account ID first),
     * we guarantee that if Thread-A is holding the lock on the lower-ID account,
     * Thread-B cannot be holding the lock on the higher-ID account while waiting
     * for the lower one — Thread-B must also acquire the lower-ID lock first,
     * so it will simply wait behind Thread-A rather than creating a cycle.
     * This is a well-known technique called "lock ordering" or "resource
     * hierarchy" and it mathematically proves deadlock-freedom.
     * </p>
     */
    @Override
    public TransactionRecord transfer(BankAccount from, BankAccount to, long amount) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Accounts cannot be null");
        }
        if (from == to) {
            return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
        }

        // Lock ordering to prevent deadlocks: always lock account with smaller ID first
        BankAccount first = from.getId() < to.getId() ? from : to;
        BankAccount second = from.getId() < to.getId() ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (!from.withdraw(amount)) {
                    return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.FAILED);
                }
                to.deposit(amount);
                return new TransactionRecord(from.getId(), to.getId(), amount, TransactionRecord.Status.SUCCESS);
            }
        }
    }
}
