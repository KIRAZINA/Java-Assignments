package bank;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Test Suite for Bank Account Simulator
 *
 * <p>This test suite includes all unit tests, integration tests, stress tests,
 * and performance benchmarks for the Bank Account Simulator project.</p>
 *
 * <p>Run this suite to execute all tests in the correct order:</p>
 * <ol>
 *   <li>Unit tests for individual components</li>
 *   <li>Integration tests for concurrent scenarios</li>
 *   <li>Stress tests verifying concurrency invariants</li>
 *   <li>Deadlock detection tests</li>
 *   <li>Performance benchmarks</li>
 * </ol>
 */
@Suite
@SelectClasses({
    // Unit Tests
    BankAccountTest.class,
    TransactionRecordTest.class,
    TransferServiceUnsafeTest.class,
    TransferServiceSynchronizedTest.class,
    TransferServiceLockTest.class,
    TransferServiceStampedLockTest.class,

    // Stress & Invariant Tests
    InvariantStressTest.class,

    // Deadlock Detection Tests
    DeadlockDetectionTest.class,

    // Integration Tests
    ConcurrentTransferIntegrationTest.class,

    // Performance Tests
    PerformanceBenchmarkTest.class
})
public class TestSuite {
    // Test suite class - no implementation needed
}
