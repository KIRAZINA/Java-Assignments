package bank;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;

/**
 * SimulationRunner with Scheduled Deadlock Monitoring
 *
 * Periodically launches waves of transfers and checks for deadlocks.
 */
public class SimulationRunnerWithDeadlockMonitor {

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numThreads = 20;
        int waveSize = 100;

        BankAccount[] accounts = initAccounts(numAccounts, initialBalance);
        long totalBefore = totalBalance(accounts);
        TransferServiceLock sharedService = new TransferServiceLock(); // Shared instance

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Runnable waveTask = () -> {
            System.out.println("\n🌊 New wave started at " + System.currentTimeMillis());
            CyclicBarrier barrier = new CyclicBarrier(waveSize,
                    () -> System.out.println("🚀 Wave released!"));

            CountDownLatch latch = new CountDownLatch(waveSize);
            ThreadLocal<java.util.Random> random = ThreadLocal.withInitial(java.util.Random::new);

            for (int i = 0; i < waveSize; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Thread interrupted while waiting for barrier: " + e.getMessage());
                        return;
                    }

                    try {
                        int fromIndex = random.get().nextInt(accounts.length);
                        int toIndex = random.get().nextInt(accounts.length);
                        long amount = 1 + random.get().nextInt(50);

                        TransactionRecord record = sharedService.transfer(accounts[fromIndex], accounts[toIndex], amount);

                        System.out.println(record);
                    } catch (Exception e) {
                        System.err.println("Error in transfer operation: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("Wave finished. Current total balance: " + totalBalance(accounts));
        };

        Runnable deadlockMonitor = () -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreads != null) {
                System.err.println("⚠️ DEADLOCK DETECTED! Threads: " + java.util.Arrays.toString(deadlockedThreads));
            } else {
                System.out.println("✅ No deadlocks detected at " + System.currentTimeMillis());
            }
        };

        // Schedule waves every 5 seconds
        scheduler.scheduleAtFixedRate(waveTask, 0, 5, TimeUnit.SECONDS);

        // Schedule deadlock monitoring every 3 seconds
        scheduler.scheduleAtFixedRate(deadlockMonitor, 0, 3, TimeUnit.SECONDS);

        // Let simulation run for 30 seconds
        Thread.sleep(30_000);

        // Graceful shutdown
        scheduler.shutdown();
        executor.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            executor.shutdownNow();
        }

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
        return java.util.Arrays.stream(accounts).mapToLong(BankAccount::getBalance).sum();
    }
}
