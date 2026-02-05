package bank;

import java.util.concurrent.*;
import java.util.*;

/**
 * SimulationRunner with CyclicBarrier
 *
 * Demonstrates wave-based concurrent transfers where threads are released in waves.
 * Fixed: Barrier now correctly handles wave-based execution.
 */
public class SimulationRunnerWithBarrier {

    private static final int NUM_ACCOUNTS = 10;
    private static final long INITIAL_BALANCE = 1000;
    private static final int NUM_TRANSFERS = 1000;
    private static final int WAVE_SIZE = 100; // Number of threads per wave
    private static final int MAX_TRANSFER_AMOUNT = 50;

    public static void main(String[] args) throws InterruptedException {
        BankAccount[] accounts = initAccounts(NUM_ACCOUNTS, INITIAL_BALANCE);
        long totalBefore = totalBalance(accounts);

        // Calculate number of complete waves
        int numWaves = NUM_TRANSFERS / WAVE_SIZE;
        int remainingTransfers = NUM_TRANSFERS % WAVE_SIZE;

        ExecutorService executor = Executors.newFixedThreadPool(WAVE_SIZE);
        TransferServiceLock service = new TransferServiceLock();

        long start = System.nanoTime();

        // Execute complete waves
        for (int wave = 0; wave < numWaves; wave++) {
            executeWave(executor, accounts, service, WAVE_SIZE, wave + 1);
        }

        // Execute remaining transfers if any
        if (remainingTransfers > 0) {
            executeWave(executor, accounts, service, remainingTransfers, numWaves + 1);
        }

        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            System.err.println("Executor did not terminate in time");
            executor.shutdownNow();
        }

        long end = System.nanoTime();
        long totalAfter = totalBalance(accounts);

        System.out.printf("Before=%d, After=%d, time=%d ms%n",
                totalBefore, totalAfter, (end - start) / 1_000_000);
    }

    private static void executeWave(ExecutorService executor, BankAccount[] accounts,
                                   TransferServiceLock service, int waveSize, int waveNumber)
            throws InterruptedException {
        
        CyclicBarrier barrier = new CyclicBarrier(waveSize,
                () -> System.out.println("Wave " + waveNumber + " started! All threads released simultaneously."));

        CountDownLatch latch = new CountDownLatch(waveSize);

        for (int i = 0; i < waveSize; i++) {
            executor.submit(() -> {
                try {
                    // Wait until all threads in this wave are ready
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Thread interrupted or barrier broken: " + e.getMessage());
                    return;
                } finally {
                    latch.countDown();
                }

                // Use ThreadLocalRandom for thread-safe random number generation
                int fromIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                int toIndex = ThreadLocalRandom.current().nextInt(accounts.length);
                long amount = 1 + ThreadLocalRandom.current().nextInt(MAX_TRANSFER_AMOUNT);

                TransactionRecord record = service.transfer(accounts[fromIndex], accounts[toIndex], amount);

                // Optional: log transaction
                if (record.getStatus() != TransactionRecord.Status.SUCCESS) {
                    System.out.println(record);
                }
            });
        }

        // Wait for all threads in this wave to complete
        latch.await();
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
