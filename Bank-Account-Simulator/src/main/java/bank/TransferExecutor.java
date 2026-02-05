package bank;

/**
 * Helper class to execute transfers with different service implementations.
 * Eliminates code duplication across simulation runners.
 */
public class TransferExecutor {

    /**
     * Execute a transfer using the appropriate service implementation.
     * 
     * @param service The transfer service (Unsafe, Synchronized, or Lock)
     * @param from Source account
     * @param to Destination account
     * @param amount Amount to transfer
     * @return TransactionRecord with the result
     */
    public static TransactionRecord executeTransfer(Object service, BankAccount from, BankAccount to, long amount) {
        if (service instanceof TransferServiceUnsafe) {
            boolean success = ((TransferServiceUnsafe) service).transfer(from, to, amount);
            return new TransactionRecord(
                from.getId(), 
                to.getId(), 
                amount,
                success ? TransactionRecord.Status.SUCCESS : TransactionRecord.Status.FAILED
            );
        } else if (service instanceof TransferServiceSynchronized) {
            boolean success = ((TransferServiceSynchronized) service).transfer(from, to, amount);
            return new TransactionRecord(
                from.getId(), 
                to.getId(), 
                amount,
                success ? TransactionRecord.Status.SUCCESS : TransactionRecord.Status.FAILED
            );
        } else if (service instanceof TransferServiceLock) {
            return ((TransferServiceLock) service).transfer(from, to, amount);
        } else {
            throw new IllegalArgumentException("Unknown service type: " + service.getClass().getName());
        }
    }
}
