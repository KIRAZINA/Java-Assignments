package bank;

/**
 * Common contract for all bank-account transfer service implementations.
 *
 * <p>Each implementation uses a different concurrency strategy to guarantee
 * thread-safe fund transfers between {@link BankAccount} instances. Implementations
 * must return a {@link TransactionRecord} describing the outcome so that the
 * {@link SimulationEngine} can record metrics and maintain an audit trail.</p>
 *
 * <p>Implementations may vary in locking strategy:</p>
 * <ul>
 *   <li>{@link TransferServiceUnsafe} – no synchronization (demonstrates race conditions)</li>
 *   <li>{@link TransferServiceSynchronized} – intrinsic {@code synchronized} monitors with lock ordering</li>
 *   <li>{@link TransferServiceLock} – {@link java.util.concurrent.locks.ReentrantLock} with tryLock + rollback</li>
 *   <li>{@link TransferServiceStampedLock} – {@link java.util.concurrent.locks.StampedLock} with optimistic reads</li>
 * </ul>
 */
public interface TransferService {

    /**
     * Atomically transfer {@code amount} from {@code from} to {@code to}.
     *
     * @param from   the source account (must not be {@code null})
     * @param to     the destination account (must not be {@code null})
     * @param amount the amount to transfer (must be positive)
     * @return a {@link TransactionRecord} describing the result
     */
    TransactionRecord transfer(BankAccount from, BankAccount to, long amount);
}
