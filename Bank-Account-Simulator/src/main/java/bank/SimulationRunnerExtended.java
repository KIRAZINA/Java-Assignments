package bank;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;

/**
 * Extended SimulationRunner
 *
 * Adds:
 * - Deadlock detection via ThreadMXBean
 * - Execution time measurement
 * - Logging of failed transfers
 */
public class SimulationRunnerExtended {

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numTransfers = 20000;
        int numThreads = 8;

        runWithMonitoring("UNSAFE", new TransferServiceUnsafe(), numAccounts, initialBalance, numTransfers, numThreads);
        runWithMonitoring("SAFE (synchronized)", new TransferServiceSynchronized(), numAccounts, initialBalance, numTransfers, numThreads);
        runWithMonitoring("SAFE (ReentrantLock)", new TransferServiceLock(), numAccounts, initialBalance, numTransfers, numThreads);

        // Deadlock detection
        detectDeadlocks();
    }

    private static void runWithMonitoring(String label, Object service,
                                          int numAccounts, long initialBalance,
                                          int numTransfers, int numThreads) throws InterruptedException {
        BankAccount[] accounts = initAccounts(numAccounts, initialBalance);
        long totalBefore = totalBalance(accounts);

        long start = System.nanoTime();
        int failed = runSimulation(service, accounts, numTransfers, numThreads);
        long end = System.nanoTime();

        long totalAfter = totalBalance(accounts);

        System.out.printf("%s: before=%d, after=%d, failed=%d, time=%d ms%n",
                label, totalBefore, totalAfter, failed, (end - start) / 1_000_000);
    }

    private static BankAccount[] initAccounts(int numAccounts, long initialBalance) {
        BankAccount[] accounts = new BankAccount[numAccounts];
        for (int i = 0; i < numAccounts; i++) {
            accounts[i] = new BankAccount(i, initialBalance);
        }
        return accounts;
    }

    private static int runSimulation(Object service, BankAccount[] accounts,
                                     int numTransfers, int numThreads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numTransfers);
        ThreadLocal<java.util.Random> random = ThreadLocal.withInitial(java.util.Random::new);

        final int[] failedTransfers = {0};

        for (int i = 0; i < numTransfers; i++) {
            executor.submit(() -> {
                int fromIndex = random.get().nextInt(accounts.length);
                int toIndex = random.get().nextInt(accounts.length);
                long amount = 1 + random.get().nextInt(50);

                // Use TransferExecutor to eliminate code duplication
                TransactionRecord record = TransferExecutor.executeTransfer(
                    service,
                    accounts[fromIndex],
                    accounts[toIndex],
                    amount
                );

                if (record.getStatus() != TransactionRecord.Status.SUCCESS) {
                    synchronized (failedTransfers) {
                        failedTransfers[0]++;
                    }
                }

                // Optional: log transaction
                // System.out.println(record);

                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        return failedTransfers[0];
    }

    private static long totalBalance(BankAccount[] accounts) {
        return java.util.Arrays.stream(accounts).mapToLong(BankAccount::getBalance).sum();
    }

    private static void detectDeadlocks() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        if (deadlockedThreads != null) {
            System.err.println("DEADLOCK DETECTED! Threads: " + java.util.Arrays.toString(deadlockedThreads));
        } else {
            System.out.println("No deadlocks detected.");
        }
    }
}
