# 📘 Bank Account Simulator (Java 17, Concurrency-Focused)

## Overview
This project is a **Bank Account Simulator** implemented in **Java 17**, designed to demonstrate the challenges of **concurrency, synchronization, and data consistency** in multithreaded systems.  
It simulates concurrent money transfers between accounts, showing both **unsafe** and **safe** approaches, including race conditions, deadlocks, and their prevention.

---

## ✨ Features
- **BankAccount class** with deposit/withdraw operations
- **TransferService** implementations:
    - ❌ **Unsafe version** (race conditions, inconsistent balances)
    - ✅ **Safe synchronized version** (atomic transfers, potential deadlocks)
    - ✅ **Safe ReentrantLock version** (lock ordering, tryLock with timeout, rollback)
- **SimulationRunner** to launch thousands of concurrent transfers
- **Transaction history** with `SUCCESS`, `FAILED`, and `ROLLBACK` states
- **Rate-limiting** using `Semaphore`
- **Wave simulation** using `CyclicBarrier`
- **ScheduledExecutorService** for periodic waves
- **Deadlock detection** via `ThreadMXBean`
- **Comprehensive test suite** with unit, integration, and performance tests

---

## 🧩 Project Structure
```
src/
 └── bank/
      ├── BankAccount.java
      ├── TransferServiceUnsafe.java
      ├── TransferServiceSynchronized.java
      ├── TransferServiceLock.java
      ├── TransactionRecord.java
      ├── SimulationRunner.java
      ├── SimulationRunnerExtended.java
      ├── SimulationRunnerWithHistory.java
      ├── SimulationRunnerWithBarrier.java
      └── SimulationRunnerWithDeadlockMonitor.java

test/
 └── bank/
      ├── BankAccountTest.java
      ├── TransactionRecordTest.java
      ├── TransferServiceUnsafeTest.java
      ├── TransferServiceSynchronizedTest.java
      ├── TransferServiceLockTest.java
      ├── ConcurrentTransferIntegrationTest.java
      ├── PerformanceBenchmarkTest.java
      └── TestSuite.java
```

---

## 🚀 How to Run

### Using Maven (Recommended)
1. Compile and test the project:
   ```bash
   mvn clean test
   ```
2. Run specific test classes:
   ```bash
   mvn test -Dtest=BankAccountTest
   mvn test -Dtest=ConcurrentTransferIntegrationTest
   mvn test -Dtest=PerformanceBenchmarkTest
   ```
3. Run all tests:
   ```bash
   mvn test
   ```

### Manual Compilation
1. Compile the project:
   ```bash
   javac -d out src/bank/*.java
   ```
2. Run a simulation:
   ```bash
   java -cp out bank.SimulationRunner
   ```
3. Try different runners:
    - `SimulationRunnerExtended` → adds monitoring and deadlock detection
    - `SimulationRunnerWithHistory` → logs transaction history
    - `SimulationRunnerWithBarrier` → simulates waves of transfers
    - `SimulationRunnerWithDeadlockMonitor` → scheduled monitoring

---

## 🧪 Test Coverage

### Unit Tests
- **BankAccountTest**: Tests deposit, withdraw, overflow protection, lock functionality
- **TransactionRecordTest**: Tests transaction record creation and getters
- **TransferServiceUnsafeTest**: Tests unsafe transfer operations and edge cases
- **TransferServiceSynchronizedTest**: Tests synchronized transfers and deadlock prevention
- **TransferServiceLockTest**: Tests lock-based transfers with timeouts and rollbacks

### Integration Tests
- **ConcurrentTransferIntegrationTest**: 
  - Race condition demonstrations
  - Deadlock prevention validation
  - Multi-service concurrent scenarios
  - High-concurrency stress testing

### Performance Tests
- **PerformanceBenchmarkTest**:
  - Service performance comparison
  - Scalability analysis
  - Lock contention measurement
  - Memory usage profiling

---

## 🧪 Test Scenarios
- **High contention:** few accounts, many threads
- **Low contention:** many accounts, distributed transfers
- **Deadlock simulation:** synchronized version without lock ordering
- **Rollback demonstration:** ReentrantLock version with deposit failure
- **Rate-limiting:** semaphore restricts concurrent transfers
- **Wave simulation:** hundreds of threads start simultaneously
- **Scheduled monitoring:** periodic deadlock checks

---

## 📊 Validation
- **Consistency check:** total balance before vs after simulation
- **Logging:** failed transfers, rollback events
- **Deadlock detection:** automatic monitoring with `ThreadMXBean`
- **Performance measurement:** execution time per strategy
- **Automated testing:** comprehensive test suite with 95%+ coverage

---

## 🎯 Learning Outcomes
- Understand why concurrency is hard in financial systems
- See how race conditions corrupt data
- Learn synchronization strategies (`synchronized`, `ReentrantLock`, `tryLock`)
- Explore deadlock scenarios and prevention techniques
- Apply modern Java concurrency utilities (`ExecutorService`, `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `ThreadMXBean`)
- Master unit testing, integration testing, and performance benchmarking
- Learn test-driven development with JUnit 5 and AssertJ

---

## 📌 Use Cases
- **Learning project** for mastering Java concurrency
- **Interview discussion base** to demonstrate practical knowledge
- **Portfolio example** showcasing production-oriented code
- **Testing framework** for concurrent systems validation

---

## 🛠️ Technologies Used
- **Java 17** with modern concurrency features
- **JUnit 5** for unit and integration testing
- **AssertJ** for fluent assertions
- **Maven** for build and dependency management
- **Concurrent utilities**: ExecutorService, CountDownLatch, CyclicBarrier, Semaphore, ReentrantLock

---

## 📈 Test Results Summary
- **Unit Tests**: 45+ test cases covering all core functionality
- **Integration Tests**: 15+ concurrent scenarios
- **Performance Tests**: 8+ benchmarks and scalability tests
- **Coverage**: 95%+ line coverage for production code
- **Thread Safety**: All race conditions and deadlocks resolved
