package bank;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Test Suite for Bank Account Simulator
 * 
 * This test suite includes all unit tests, integration tests, and performance benchmarks
 * for the Bank Account Simulator project.
 * 
 * Run this suite to execute all tests in the correct order:
 * 1. Unit tests for individual components
 * 2. Integration tests for concurrent scenarios
 * 3. Performance benchmarks
 */
@Suite
@SelectClasses({
    // Unit Tests
    BankAccountTest.class,
    TransactionRecordTest.class,
    TransferServiceUnsafeTest.class,
    TransferServiceSynchronizedTest.class,
    TransferServiceLockTest.class,
    
    // Integration Tests
    ConcurrentTransferIntegrationTest.class,
    
    // Performance Tests
    PerformanceBenchmarkTest.class
})
public class TestSuite {
    // Test suite class - no implementation needed
}
