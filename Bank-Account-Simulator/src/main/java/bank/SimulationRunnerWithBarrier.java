package bank;

import java.util.concurrent.*;
import java.util.*;

/**
 * SimulationRunner with CyclicBarrier
 *
 * Demonstrates wave-based concurrent transfers.
 */
public class SimulationRunnerWithBarrier {

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numTransfers = 1000;
        int numThreads = 100; // simulate 100 concurrent threads

        BankAccount[] accounts = initAccounts(numAccounts, initialBalance);
        long totalBefore = totalBalance(accounts);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads,
                () -> System.out.println("🚀 Wave started! All threads released simultaneously."));

        CountDownLatch latch = new CountDownLatch(numTransfers);
        Random random = new Random();

        long start = System.nanoTime();

        for (int i = 0; i < numTransfers; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(); // wait until all threads are ready
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }

                int fromIndex = random.nextInt(accounts.length);
                int toIndex = random.nextInt(accounts.length);
                long amount = 1 + random.nextInt(50);

                // Using safe ReentrantLock service with rollback
                TransferServiceLock service = new TransferServiceLock();
                TransactionRecord record = service.transfer(accounts[fromIndex], accounts[toIndex], amount);

                // Optional: log transaction
                System.out.println(record);

                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        long end = System.nanoTime();
        long totalAfter = totalBalance(accounts);

        System.out.printf("Before=%d, After=%d, time=%d ms%n",
                totalBefore, totalAfter, (end - start) / 1_000_000);
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
