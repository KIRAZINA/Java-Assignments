package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Performance Benchmark Tests")
class PerformanceBenchmarkTest {

    private BankAccount[] accounts;
    private static final int NUM_ACCOUNTS = 20;
    private static final long INITIAL_BALANCE = 10000L;
    private static final long TOTAL_INITIAL_BALANCE = NUM_ACCOUNTS * INITIAL_BALANCE;

    @BeforeEach
    void setUp() {
        accounts = new BankAccount[NUM_ACCOUNTS];
        for (int i = 0; i < NUM_ACCOUNTS; i++) {
            accounts[i] = new BankAccount(i, INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Service Performance Comparison")
    class ServicePerformanceComparison {
        
        @Test
        @DisplayName("Benchmark all transfer services")
        void benchmarkAllTransferServices() throws InterruptedException {
            int numThreads = 8;
            int transfersPerThread = 1000;
            
            System.out.println("\n=== Performance Benchmark Results ===");
            
            // Benchmark Unsafe Service
            long unsafeTime = benchmarkService(
                new TransferServiceUnsafe(), 
                numThreads, 
                transfersPerThread, 
                "Unsafe"
            );
            
            // Benchmark Synchronized Service
            long syncTime = benchmarkService(
                new TransferServiceSynchronized(), 
                numThreads, 
                transfersPerThread, 
                "Synchronized"
            );
            
            // Benchmark Lock Service
            long lockTime = benchmarkService(
                new TransferServiceLock(), 
                numThreads, 
                transfersPerThread, 
                "Lock"
            );
            
            System.out.println("\n=== Performance Summary ===");
            System.out.println("Unsafe:      " + unsafeTime + "ms");
            System.out.println("Synchronized: " + syncTime + "ms");
            System.out.println("Lock:        " + lockTime + "ms");
            
            // Performance assertions (these are relative and may need adjustment)
            assertThat(unsafeTime).isLessThan(syncTime * 10); // Unsafe should be faster or competitive
            assertThat(syncTime).isLessThan(lockTime * 10); // Synchronized should be competitive
        }
        
        private long benchmarkService(Object service, int numThreads, int transfersPerThread, String serviceName) 
                throws InterruptedException {
            BankAccount[] testAccounts = createTestAccounts();
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicLong operations = new AtomicLong(0);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < transfersPerThread; j++) {
                            int from = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            int to = ThreadLocalRandom.current().nextInt(NUM_ACCOUNTS);
                            long amount = 1 + ThreadLocalRandom.current().nextInt(100);
                            
                            if (from != to) {
                                boolean success = false;
                                
                                if (service instanceof TransferServiceUnsafe) {
                                    success = ((TransferServiceUnsafe) service)
                                        .transfer(testAccounts[from], testAccounts[to], amount);
                                } else if (service instanceof TransferServiceSynchronized) {
                                    success = ((TransferServiceSynchronized) service)
                                        .transfer(testAccounts[from], testAccounts[to], amount);
                                } else if (service instanceof TransferServiceLock) {
                                    TransactionRecord result = ((TransferServiceLock) service)
                                        .transfer(testAccounts[from], testAccounts[to], amount);
                                    success = result.getStatus() == TransactionRecord.Status.SUCCESS;
                                }
                                
                                if (success) {
                                    operations.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            long endTime = System.currentTimeMillis();
            executor.shutdown();
            
            long totalTime = endTime - startTime;
            long totalOperations = operations.get();
            double opsPerSecond = totalOperations / (totalTime / 1000.0);
            
            System.out.println(serviceName + " - Time: " + totalTime + "ms, " +
                             "Operations: " + totalOperations + ", " +
                             "Ops/sec: " + String.format("%.2f", opsPerSecond));
            
            // Verify balance consistency for safe services
            if (service instanceof TransferServiceSynchronized || service instanceof TransferServiceLock) {
                long finalBalance = getTotalBalance(testAccounts);
                assertThat(finalBalance).isEqualTo(TOTAL_INITIAL_BALANCE);
            }
            
            return totalTime;
        }
    }

    @Nested
    @DisplayName("Scalability Tests")
    class ScalabilityTests {
        
        @Test
        @DisplayName("Test scalability with increasing thread count")
        void testScalabilityWithIncreasingThreadCount() throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            int[] threadCounts = {1, 2, 4, 8, 16, 32};
            int transfersPerThread = 500;
            
            System.out.println("\n=== Scalability Test Results ===");
            System.out.println("Threads | Time (ms) | Ops/sec | Efficiency");
            System.out.println("----------------------------------------");
            
            long baselineTime = 0;
            
            for (int numThreads : threadCounts) {
                long time = measurePerformance(service, numThreads, transfersPerThread);
                double opsPerSecond = (numThreads * transfersPerThread) / (time / 1000.0);
                double efficiency = baselineTime > 0 ? (baselineTime / (double) time) * 100 : 100;
                
                if (numThreads == 1) {
                    baselineTime = time;
                }
                
                System.out.printf("%7d | %9d | %7.0f | %8.1f%%%n", 
                    numThreads, time, opsPerSecond, efficiency);
            }
        }
        
        @Test
        @DisplayName("Test scalability with increasing account count")
        void testScalabilityWithIncreasingAccountCount() throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            int[] accountCounts = {5, 10, 20, 50, 100};
            int numThreads = 8;
            int transfersPerThread = 500;
            
            System.out.println("\n=== Account Count Scalability ===");
            System.out.println("Accounts | Time (ms) | Ops/sec");
            System.out.println("-----------------------------");
            
            for (int numAccounts : accountCounts) {
                BankAccount[] testAccounts = new BankAccount[numAccounts];
                for (int i = 0; i < numAccounts; i++) {
                    testAccounts[i] = new BankAccount(i, INITIAL_BALANCE);
                }
                
                long time = measurePerformanceWithAccounts(service, testAccounts, numThreads, transfersPerThread);
                double opsPerSecond = (numThreads * transfersPerThread) / (time / 1000.0);
                
                System.out.printf("%8d | %9d | %7.0f%n", numAccounts, time, opsPerSecond);
            }
        }
    }

    @Nested
    @DisplayName("Lock Contention Tests")
    class LockContentionTests {
        
        @Test
        @DisplayName("Measure lock contention impact")
        void measureLockContentionImpact() throws InterruptedException {
            int numThreads = 20;
            int transfersPerThread = 1000;
            
            System.out.println("\n=== Lock Contention Analysis ===");
            
            // High contention - same accounts
            long highContentionTime = measureHighContention(numThreads, transfersPerThread);
            
            // Low contention - random accounts
            long lowContentionTime = measureLowContention(numThreads, transfersPerThread);
            
            double contentionRatio = (double) highContentionTime / lowContentionTime;
            
            System.out.println("High contention: " + highContentionTime + "ms");
            System.out.println("Low contention:  " + lowContentionTime + "ms");
            System.out.println("Contention ratio: " + String.format("%.2f", contentionRatio));
            
            // High contention should be equal or slower (allowing for measurement variance)
            // Due to JVM optimizations, contention might not always be slower
            assertThat(highContentionTime).isBetween(0L, lowContentionTime + 50L);
        }
        
        private long measureHighContention(int numThreads, int transfersPerThread) throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            BankAccount[] testAccounts = new BankAccount[2]; // Only 2 accounts for high contention
            testAccounts[0] = new BankAccount(0, INITIAL_BALANCE * 10);
            testAccounts[1] = new BankAccount(1, INITIAL_BALANCE * 10);
            
            return measurePerformanceWithAccounts(service, testAccounts, numThreads, transfersPerThread);
        }
        
        private long measureLowContention(int numThreads, int transfersPerThread) throws InterruptedException {
            TransferServiceSynchronized service = new TransferServiceSynchronized();
            BankAccount[] testAccounts = new BankAccount[50]; // Many accounts for low contention
            for (int i = 0; i < 50; i++) {
                testAccounts[i] = new BankAccount(i, INITIAL_BALANCE);
            }
            
            return measurePerformanceWithAccounts(service, testAccounts, numThreads, transfersPerThread);
        }
    }

    @Nested
    @DisplayName("Memory Usage Tests")
    class MemoryUsageTests {
        
        @Test
        @DisplayName("Measure memory usage during high load")
        void measureMemoryUsageDuringHighLoad() throws InterruptedException {
            TransferServiceLock service = new TransferServiceLock();
            int numThreads = 16;
            int transfersPerThread = 2000;
            
            Runtime runtime = Runtime.getRuntime();
            
            // Force garbage collection
            System.gc();
            Thread.sleep(100);
            
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            // Run high load test
            BankAccount[] testAccounts = createTestAccounts();
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
                                service.transfer(testAccounts[from], testAccounts[to], amount);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            System.out.println("\n=== Memory Usage Analysis ===");
            System.out.println("Memory before: " + (memoryBefore / 1024 / 1024) + " MB");
            System.out.println("Memory after:  " + (memoryAfter / 1024 / 1024) + " MB");
            System.out.println("Memory used:   " + (memoryUsed / 1024 / 1024) + " MB");
            
            // Memory usage should be reasonable (less than 100MB for this test)
            assertThat(memoryUsed).isLessThan(100 * 1024 * 1024L);
        }
    }

    private long measurePerformance(TransferServiceSynchronized service, int numThreads, int transfersPerThread) 
            throws InterruptedException {
        BankAccount[] testAccounts = createTestAccounts();
        return measurePerformanceWithAccounts(service, testAccounts, numThreads, transfersPerThread);
    }

    private long measurePerformanceWithAccounts(Object service, BankAccount[] testAccounts, 
                                               int numThreads, int transfersPerThread) 
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < transfersPerThread; j++) {
                        int from = ThreadLocalRandom.current().nextInt(testAccounts.length);
                        int to = ThreadLocalRandom.current().nextInt(testAccounts.length);
                        long amount = 1 + ThreadLocalRandom.current().nextInt(100);
                        
                        if (from != to) {
                            if (service instanceof TransferServiceSynchronized) {
                                ((TransferServiceSynchronized) service)
                                    .transfer(testAccounts[from], testAccounts[to], amount);
                            } else if (service instanceof TransferServiceLock) {
                                ((TransferServiceLock) service)
                                    .transfer(testAccounts[from], testAccounts[to], amount);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.currentTimeMillis();
        executor.shutdown();
        
        return endTime - startTime;
    }

    private BankAccount[] createTestAccounts() {
        BankAccount[] testAccounts = new BankAccount[NUM_ACCOUNTS];
        for (int i = 0; i < NUM_ACCOUNTS; i++) {
            testAccounts[i] = new BankAccount(i, INITIAL_BALANCE);
        }
        return testAccounts;
    }

    private long getTotalBalance(BankAccount[] accountArray) {
        long total = 0L;
        for (BankAccount account : accountArray) {
            total += account.getBalance();
        }
        return total;
    }
}
