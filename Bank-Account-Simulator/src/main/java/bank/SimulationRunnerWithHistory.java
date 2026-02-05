package bank;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.*;

/**
 * SimulationRunner with Transaction History
 */
public class SimulationRunnerWithHistory {

    private static final ConcurrentLinkedQueue<TransactionRecord> history = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) throws InterruptedException {
        int numAccounts = 10;
        long initialBalance = 1000;
        int numTransfers = 20000;
        int numThreads = 8;

        runWithHistory("UNSAFE", new TransferServiceUnsafe(), numAccounts, initialBalance, numTransfers, numThreads);
        runWithHistory("SAFE (synchronized)", new TransferServiceSynchronized(), numAccounts, initialBalance, numTransfers, numThreads);
        runWithHistory("SAFE (ReentrantLock)", new TransferServiceLock(), numAccounts, initialBalance, numTransfers, numThreads);

        // Print last 10 transactions for inspection
        System.out.println("\n--- Last 10 Transactions ---");
        history.stream().skip(Math.max(0, history.size() - 10)).forEach(System.out::println);
    }

    private static void runWithHistory(String label, Object service,
                                       int numAccounts, long initialBalance,
                                       int numTransfers, int numThreads) throws InterruptedException {
        BankAccount[] accounts = initAccounts(numAccounts, initialBalance);
        long totalBefore = totalBalance(accounts);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numTransfers);
        ThreadLocal<Random> random = ThreadLocal.withInitial(Random::new);
        AtomicInteger failedTransfers = new AtomicInteger();

        long start = System.nanoTime();

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
                    failedTransfers.incrementAndGet();
                }

                // Record transaction in history
                history.add(record);

                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        long end = System.nanoTime();
        long totalAfter = totalBalance(accounts);

        System.out.printf("%s: before=%d, after=%d, failed=%d, time=%d ms%n",
                label, totalBefore, totalAfter, failedTransfers.get(), (end - start) / 1_000_000);
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
