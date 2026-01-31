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
```

---

## 🚀 How to Run
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

---

## 🎯 Learning Outcomes
- Understand why concurrency is hard in financial systems
- See how race conditions corrupt data
- Learn synchronization strategies (`synchronized`, `ReentrantLock`, `tryLock`)
- Explore deadlock scenarios and prevention techniques
- Apply modern Java concurrency utilities (`ExecutorService`, `CountDownLatch`, `CyclicBarrier`, `Semaphore`, `ThreadMXBean`)

---

## 📌 Use Cases
- **Learning project** for mastering Java concurrency
- **Interview discussion base** to demonstrate practical knowledge
- **Portfolio example** showcasing production-oriented code
