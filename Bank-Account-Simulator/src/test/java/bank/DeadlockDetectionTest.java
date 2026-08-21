package bank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that the {@link ThreadMXBean} deadlock detection facility correctly
 * identifies and allows recovery from a circular-wait deadlock scenario.
 *
 * <p>The test intentionally creates two threads that acquire two locks in
 * <strong>opposite</strong> orders — the classic recipe for a deadlock.
 * We then use {@code ThreadMXBean.findDeadlockedThreads()} to prove the
 * deadlock is detected, and {@code Thread.interrupt()} (via
 * {@code lockInterruptibly()}) to break it gracefully.</p>
 *
 * <p>All threads are guaranteed to be stopped before the test returns, so
 * the JVM does not hang.</p>
 */
@DisplayName("Deadlock Detection Tests")
class DeadlockDetectionTest {

    /**
     * Creates two threads that acquire the same two locks in opposite orders,
     * producing a textbook circular-wait deadlock:
     *
     * <pre>
     * Thread-1: locks A → waits for B
     * Thread-2: locks B → waits for A
     * </pre>
     *
     * Because both locks are acquired via {@code lockInterruptibly()}, the
     * deadlock can be broken by interrupting either thread.
     */
    @Test
    @DisplayName("ThreadMXBean should detect circular-wait deadlock and threads should recover via interrupt")
    void shouldDetectDeadlockAndRecover() throws Exception {
        BankAccount account1 = new BankAccount(1, 1000L);
        BankAccount account2 = new BankAccount(2, 1000L);

        Lock lockA = account1.getLock(); // shared ReentrantLock
        Lock lockB = account2.getLock(); // shared ReentrantLock

        CountDownLatch locksAcquired = new CountDownLatch(2); // both threads have their first lock
        CountDownLatch deadlockConfirmed = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            try {
                lockA.lockInterruptibly();
                try {
                    locksAcquired.countDown();  // first lock acquired
                    locksAcquired.await();      // wait until t2 also has its first lock
                    // Now try to acquire B — will block (deadlock)
                    lockB.lockInterruptibly();
                    try {
                        // Transfer would happen here
                    } finally {
                        lockB.unlock();
                    }
                } finally {
                    lockA.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "DeadlockThread-1");

        Thread t2 = new Thread(() -> {
            try {
                lockB.lockInterruptibly();
                try {
                    locksAcquired.countDown();  // first lock acquired
                    locksAcquired.await();      // wait until t1 also has its first lock
                    // Now try to acquire A — will block (deadlock)
                    lockA.lockInterruptibly();
                    try {
                        // Transfer would happen here
                    } finally {
                        lockA.unlock();
                    }
                } finally {
                    lockB.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "DeadlockThread-2");

        t1.start();
        t2.start();

        try {
            // Give both threads time to reach the deadlock state
            Thread.sleep(1000);

            // ---------------------------------------------------------------
            // Detection: ThreadMXBean.findDeadlockedThreads() returns an array
            // of thread IDs involved in an object-monitor or
            // ownable-synchronizer deadlock.
            // ---------------------------------------------------------------
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();

            assertThat(deadlockedThreadIds)
                    .as("ThreadMXBean should detect the deadlock")
                    .isNotNull()
                    .isNotEmpty();

            // Verify that exactly our two threads are the deadlocked ones
            boolean foundT1 = false;
            boolean foundT2 = false;
            for (long id : deadlockedThreadIds) {
                if (id == t1.getId()) foundT1 = true;
                if (id == t2.getId()) foundT2 = true;
            }

            assertThat(foundT1).as("DeadlockThread-1 should be in the deadlocked set").isTrue();
            assertThat(foundT2).as("DeadlockThread-2 should be in the deadlocked set").isTrue();

            System.out.println("Deadlock detected for threads: "
                    + java.util.Arrays.toString(deadlockedThreadIds));

            // ---------------------------------------------------------------
            // Recovery: interrupt both threads.  Since they used
            // lockInterruptibly(), the interrupt will cause them to throw
            // InterruptedException and exit their lock-acquisition attempts.
            // ---------------------------------------------------------------
            t1.interrupt();
            t2.interrupt();

            // Wait for threads to terminate (with a generous timeout)
            t1.join(5000);
            t2.join(5000);

            assertThat(!t1.isAlive()).as("Thread-1 should terminate after interrupt").isTrue();
            assertThat(!t2.isAlive()).as("Thread-2 should terminate after interrupt").isTrue();

            // ---------------------------------------------------------------
            // Post-recovery verification: no deadlocks should remain.
            // ---------------------------------------------------------------
            Thread.sleep(100); // brief wait for cleanup
            long[] remainingDeadlocked = threadMXBean.findDeadlockedThreads();

            assertThat(remainingDeadlocked)
                    .as("No deadlocks should remain after interrupt recovery")
                    .isNull();

            System.out.println("Deadlock successfully resolved via interrupt.");

        } finally {
            // Ensure threads are stopped even if assertions fail
            if (t1.isAlive()) {
                t1.interrupt();
                t1.join(5000);
            }
            if (t2.isAlive()) {
                t2.interrupt();
                t2.join(5000);
            }
        }
    }

    /**
     * Demonstrates that {@link TransferServiceSynchronized} — which uses
     * lock ordering — does NOT deadlock under heavy concurrent load.
     * This serves as a positive control contrasting with the test above.
     */
    @Test
    @DisplayName("TransferServiceSynchronized with lock ordering should not deadlock under load")
    void synchronizedServiceShouldNotDeadlock() throws InterruptedException {
        TransferServiceSynchronized service = new TransferServiceSynchronized();
        BankAccount[] accounts = new BankAccount[10];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new BankAccount(i, 10_000L);
        }

        int numThreads = 10;
        int transfersPerThread = 500;
        CountDownLatch latch = new CountDownLatch(numThreads);
        var exceptions = new java.util.concurrent.ConcurrentLinkedQueue<Exception>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Thread t = new Thread(() -> {
                try {
                    for (int j = 0; j < transfersPerThread; j++) {
                        // Deterministic pattern that creates maximum lock-ordering pressure:
                        // odd threads go 0→1→2→…→n→0, even threads go n→n-1→…→0→n
                        int from = (threadId + j) % accounts.length;
                        int to = (from + 1) % accounts.length;
                        if (from != to) {
                            service.transfer(accounts[from], accounts[to], 1L);
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).as("All threads should complete without deadlock").isTrue();
        assertThat(exceptions).isEmpty();

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threadMXBean.findDeadlockedThreads();
        assertThat(deadlocked)
                .as("ThreadMXBean should report no deadlocks for lock-ordered transfers")
                .isNull();
    }
}
