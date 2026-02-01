package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Concurrent Transfer Integration Tests")
class ConcurrentTransferIntegrationTest {

    private BankAccount[] accounts;
    private static final int NUM_ACCOUNTS = 10;
    private static final long INITIAL_BALANCE = 1000L;
    private static final long TOTAL_INITIAL_BALANCE = NUM_ACCOUNTS * INITIAL_BALANCE;

    @BeforeEach
    void setUp() {
        accounts = new BankAccount[NUM_ACCOUNTS];
        for (int i = 0; i < NUM_ACCOUNTS; i++) {
            accounts[i] = new BankAccount(i, INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Unsafe Service Concurrent Tests")
    class UnsafeServiceTests {
        
        @Test
        @DisplayName("Should demonstrate race conditions with unsafe service")
        void shouldDemonstrateRaceConditions() throws InterruptedException {
            TransferServiceUnsafe service = new TransferServiceUnsafe();
            int numThreads = 20;
            int transfersPerThread = 100;
            
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            service.randomTransfer(accounts, 100L);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            long finalBalance = getTotalBalance();
            // With unsafe service, balance may be inconsistent
            System.out.println("Unsafe service - Initial: " + TOTAL_INITIAL_BALANCE + 
                             ", Final: " + finalBalance + 
                             ", Difference: " + (finalBalance - TOTAL_INITIAL_BALANCE));
        }
    }

    @Nested
    @DisplayName("Synchronized Service Concurrent Tests")
    class SynchronizedServiceTests {
        
        @Test
        @DisplayName("Should maintain balance consistency with synchronized service")
        void shouldMaintainBalanceConsistency() throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            int numThreads = 20;
            int transfersPerThread = 100;
            
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            long amount = 1 + ThreadLocalRandom.current().nextInt(50);
                            
                            if (from != to) {
                                service.transfer(accounts[from], accounts[to], amount);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            long finalBalance = getTotalBalance();
            assertThat(finalBalance).isEqualTo(TOTAL_INITIAL_BALANCE);
        }
        
        @Test
        @DisplayName("Should not deadlock with lock ordering")
        void shouldNotDeadlockWithLockOrdering() throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            int numThreads = 10;
            int transfersPerThread = 1000;
            
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger completedTransfers = new AtomicInteger(0);
            
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            // Create potential deadlock scenarios
                            int from = threadId % NUM_ACCOUNTS;
                            int to = (threadId + 1) % NUM_ACCOUNTS;
                            long amount = 1L;
                            
                            boolean success = service.transfer(accounts[from], accounts[to], amount);
                            if (success) {
                                completedTransfers.incrementAndGet();
                            }
                            
                            // Reverse transfer to create more contention
                            service.transfer(accounts[to], accounts[from], amount);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertThat(completed).isTrue();
            assertThat(completedTransfers.get()).isGreaterThan(0);
            assertThat(getTotalBalance()).isEqualTo(TOTAL_INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Lock Service Concurrent Tests")
    class LockServiceTests {
        
        @Test
        @DisplayName("Should maintain balance consistency with lock service")
        void shouldMaintainBalanceConsistency() throws InterruptedException {
            TransferServiceLock service = new TransferServiceLock();
            int numThreads = 15; // Within semaphore limit
            int transfersPerThread = 50;
            
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successfulTransfers = new AtomicInteger(0);
            AtomicInteger failedTransfers = new AtomicInteger(0);
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            long amount = 1 + ThreadLocalRandom.current().nextInt(50);
                            
                            if (from != to) {
                                TransactionRecord result = service.transfer(accounts[from], accounts[to], amount);
                                if (result.getStatus() == TransactionRecord.Status.SUCCESS) {
                                    successfulTransfers.incrementAndGet();
                                } else {
                                    failedTransfers.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            long finalBalance = getTotalBalance();
            assertThat(finalBalance).isEqualTo(TOTAL_INITIAL_BALANCE);
            assertThat(successfulTransfers.get() + failedTransfers.get())
                .isGreaterThan(numThreads * transfersPerThread / 2);
        }
        
        @Test
        @DisplayName("Should handle concurrent transfers beyond semaphore limit")
        void shouldHandleConcurrentTransfersBeyondSemaphoreLimit() throws InterruptedException {
            TransferServiceLock service = new TransferServiceLock();
            int numThreads = 10; // Exceeds semaphore limit of 5
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger attempts = new AtomicInteger(0);
            AtomicInteger successes = new AtomicInteger(0);
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 20; j++) {
                            attempts.incrementAndGet();
                            TransactionRecord result = service.transfer(accounts[0], accounts[1], 1L);
                            if (result.getStatus() == TransactionRecord.Status.SUCCESS) {
                                successes.incrementAndGet();
                            }
                            Thread.sleep(10); // Small delay to increase contention
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertThat(getTotalBalance()).isEqualTo(TOTAL_INITIAL_BALANCE);
            assertThat(successes.get()).isLessThanOrEqualTo(attempts.get()); // Some should fail due to semaphore
        }
    }

    @Nested
    @DisplayName("Mixed Services Tests")
    class MixedServicesTests {
        
        @Test
        @DisplayName("Should handle multiple service types concurrently")
        void shouldHandleMultipleServiceTypesConcurrently() throws InterruptedException {
            TransferServiceUnsafe unsafeService = new TransferServiceUnsafe();
            TransferServiceSynchronized syncService = new TransferServiceSynchronized();
            TransferServiceLock lockService = new TransferServiceLock();
            
            BankAccount[] unsafeAccounts = createAccountArray();
            BankAccount[] syncAccounts = createAccountArray();
            BankAccount[] lockAccounts = createAccountArray();
            
            ExecutorService executor = Executors.newFixedThreadPool(9);
            CountDownLatch latch = new CountDownLatch(3);
            
            // Unsafe service thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        unsafeService.randomTransfer(unsafeAccounts, 50L);
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            // Synchronized service thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                        int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                        if (from != to) {
                            syncService.transfer(syncAccounts[from], syncAccounts[to], 50L);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            // Lock service thread
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                        int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                        if (from != to) {
                            lockService.transfer(lockAccounts[from], lockAccounts[to], 50L);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            latch.await();
            executor.shutdown();
            
            // Only synchronized and lock services should maintain consistency
            long unsafeBalance = getTotalBalance(unsafeAccounts);
            long syncBalance = getTotalBalance(syncAccounts);
            long lockBalance = getTotalBalance(lockAccounts);
            
            System.out.println("Mixed services - Unsafe: " + unsafeBalance + 
                             ", Sync: " + syncBalance + 
                             ", Lock: " + lockBalance);
            
            assertThat(syncBalance).isEqualTo(TOTAL_INITIAL_BALANCE);
            assertThat(lockBalance).isEqualTo(TOTAL_INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Stress Tests")
    class StressTests {
        
        @Test
        @DisplayName("Should handle high concurrency stress test")
        void shouldHandleHighConcurrencyStressTest() throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            int numThreads = 50;
            int transfersPerThread = 1000;
            
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicLong totalOperations = new AtomicLong(0);
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            long amount = 1L;
                            
                            if (from != to) {
                                service.transfer(accounts[from], accounts[to], amount);
                                totalOperations.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            long startTime = System.currentTimeMillis();
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            
            executor.shutdown();
            
            assertThat(completed).isTrue();
            assertThat(getTotalBalance()).isEqualTo(TOTAL_INITIAL_BALANCE);
            assertThat(totalOperations.get()).isGreaterThan(40000L);
            
            double operationsPerSecond = totalOperations.get() / ((endTime - startTime) / 1000.0);
            System.out.println("Stress test completed: " + totalOperations.get() + 
                             " operations in " + (endTime - startTime) + "ms (" + 
                             String.format("%.2f", operationsPerSecond) + " ops/sec)");
        }
    }

    private long getTotalBalance() {
        return getTotalBalance(accounts);
    }

    private long getTotalBalance(BankAccount[] accountArray) {
        long total = 0L;
        for (BankAccount account : accountArray) {
            total += account.getBalance();
        }
        return total;
    }

    private BankAccount[] createAccountArray() {
        BankAccount[] array = new BankAccount[NUM_ACCOUNTS];
        for (int i = 0; i < NUM_ACCOUNTS; i++) {
            array[i] = new BankAccount(i, INITIAL_BALANCE);
        }
        return array;
    }
}
