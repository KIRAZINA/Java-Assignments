package bank;

import java.util.concurrent.*;
import java.util.*;

/**
 * SimulationRunner with ScheduledExecutorService
 *
 * Periodically launches waves of concurrent transfers.
 */
public class SimulationRunnerScheduled {

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numThreads = 20;
        int waveSize = 100; // number of transfers per wave

        BankAccount[] accounts = initAccounts(numAccounts, initialBalance);
        long totalBefore = totalBalance(accounts);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Runnable waveTask = () -> {
            System.out.println("\nNew wave started at " + System.currentTimeMillis());
            CyclicBarrier barrier = new CyclicBarrier(waveSize,
                    () -> System.out.println("Wave released!"));

            CountDownLatch latch = new CountDownLatch(waveSize);

            for (int i = 0; i < waveSize; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Use ThreadLocalRandom for thread-safe random number generation
                    int fromIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                    int toIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                    long amount = 1 + ThreadLocalRandom.current().nextInt(50);

                    TransferServiceLock service = new TransferServiceLock();
                    TransactionRecord record = service.transfer(accounts[fromIndex], accounts[toIndex], amount);

                    System.out.println(record);
                    latch.countDown();
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("Wave finished. Current total balance: " + totalBalance(accounts));
        };

        // Schedule waves every 5 seconds
        scheduler.scheduleAtFixedRate(waveTask, 0, 5, TimeUnit.SECONDS);

        // Let simulation run for 30 seconds
        Thread.sleep(30_000);

        scheduler.shutdown();
        executor.shutdown();

        long totalAfter = totalBalance(accounts);
        System.out.printf("\nFinal total balance: before=%d, after=%d%n", totalBefore, totalAfter);
    }

    private static BankAccount[] initAccounts(int numAccounts, long initialBalance) {
        BankAccount[] accounts = new BankAccount[numAccounts];
        for (int i = 0; i < numAccounts; i++) {
            accounts[i] = new BankAccount(i, initialBalance);
        }
        return accounts;
    }

    private static long totalBalance(BankAccount[] accounts) {
        return Arrays.stream(accounts).mapToLong(BankAccount::getBalance).sum();
    }
}
