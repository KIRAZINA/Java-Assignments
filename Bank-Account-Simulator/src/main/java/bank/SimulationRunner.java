package bank;

import java.util.concurrent.*;
import java.util.*;

/**
 * SimulationRunner
 *
 * Runs concurrent transfer simulations using:
 * - Unsafe version
 * - Safe synchronized version
 * - Safe ReentrantLock version
 */
public class SimulationRunner {

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numTransfers = 10000;
        int numThreads = 8;

        // Run UNSAFE simulation
        BankAccount[] accountsUnsafe = initAccounts(numAccounts, initialBalance);
        long totalBeforeUnsafe = totalBalance(accountsUnsafe);
        runSimulation(new TransferServiceUnsafe(), accountsUnsafe, numTransfers, numThreads);
        long totalAfterUnsafe = totalBalance(accountsUnsafe);
        System.out.println("UNSAFE: before=" + totalBeforeUnsafe + ", after=" + totalAfterUnsafe);

        // Run SAFE synchronized simulation
        BankAccount[] accountsSync = initAccounts(numAccounts, initialBalance);
        long totalBeforeSync = totalBalance(accountsSync);
        runSimulation(new TransferServiceSynchronized(), accountsSync, numTransfers, numThreads);
        long totalAfterSync = totalBalance(accountsSync);
        System.out.println("SAFE (synchronized): before=" + totalBeforeSync + ", after=" + totalAfterSync);

        // Run SAFE ReentrantLock simulation
        BankAccount[] accountsLock = initAccounts(numAccounts, initialBalance);
        long totalBeforeLock = totalBalance(accountsLock);
        runSimulation(new TransferServiceLock(), accountsLock, numTransfers, numThreads);
        long totalAfterLock = totalBalance(accountsLock);
        System.out.println("SAFE (ReentrantLock): before=" + totalBeforeLock + ", after=" + totalAfterLock);
    }

    private static BankAccount[] initAccounts(int numAccounts, long initialBalance) {
        BankAccount[] accounts = new BankAccount[numAccounts];
        for (int i = 0; i < numAccounts; i++) {
            accounts[i] = new BankAccount(i, initialBalance);
        }
        return accounts;
    }

    private static void runSimulation(Object service, BankAccount[] accounts,
                                      int numTransfers, int numThreads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numTransfers);

        try {
            for (int i = 0; i < numTransfers; i++) {
                executor.submit(() -> {
                    try {
                        // Use ThreadLocalRandom for thread-safe random number generation
                        int fromIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                        int toIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                        long amount = 1 + ThreadLocalRandom.current().nextInt(50);

                        // Use TransferExecutor to eliminate code duplication
                        TransactionRecord record = TransferExecutor.executeTransfer(
                            service,
                            accounts[fromIndex],
                            accounts[toIndex],
                            amount
                        );

                        // Optional: log or store record
                        // System.out.println(record);

                    } catch (Exception e) {
                        System.err.println("Error in transfer thread: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private static long totalBalance(BankAccount[] accounts) {
        return Arrays.stream(accounts).mapToLong(BankAccount::getBalance).sum();
    }
}
