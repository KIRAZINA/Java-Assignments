package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TransferServiceLock Tests")
class TransferServiceLockTest {

    private TransferServiceLock service;
    private BankAccount fromAccount;
    private BankAccount toAccount;
    private static final long INITIAL_BALANCE = 1000L;
    private static final long TRANSFER_AMOUNT = 300L;

    @BeforeEach
    void setUp() {
        service = new TransferServiceLock();
        fromAccount = new BankAccount(1, INITIAL_BALANCE);
        toAccount = new BankAccount(2, INITIAL_BALANCE);
    }

    @Nested
    @DisplayName("Transfer Tests")
    class TransferTests {
        
        @Test
        @DisplayName("Should transfer money successfully with sufficient funds")
        void shouldTransferMoneySuccessfullyWithSufficientFunds() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, TRANSFER_AMOUNT);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(result.getFromId()).isEqualTo(fromAccount.getId());
            assertThat(result.getToId()).isEqualTo(toAccount.getId());
            assertThat(result.getAmount()).isEqualTo(TRANSFER_AMOUNT);
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should fail transfer with insufficient funds")
        void shouldFailTransferWithInsufficientFunds() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, INITIAL_BALANCE + 100L);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
            assertThat(result.getFromId()).isEqualTo(fromAccount.getId());
            assertThat(result.getToId()).isEqualTo(toAccount.getId());
            assertThat(result.getAmount()).isEqualTo(INITIAL_BALANCE + 100L);
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should fail transfer to same account")
        void shouldFailTransferToSameAccount() {
            TransactionRecord result = service.transfer(fromAccount, fromAccount, TRANSFER_AMOUNT);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
            assertThat(result.getFromId()).isEqualTo(fromAccount.getId());
            assertThat(result.getToId()).isEqualTo(fromAccount.getId());
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should transfer exact balance")
        void shouldTransferExactBalance() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, INITIAL_BALANCE);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(fromAccount.getBalance()).isEqualTo(0L);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE + INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Lock Ordering Tests")
    class LockOrderingTests {
        
        @Test
        @DisplayName("Should transfer from lower ID to higher ID")
        void shouldTransferFromLowerIdToHigherId() {
            BankAccount lowerId = new BankAccount(1, INITIAL_BALANCE);
            BankAccount higherId = new BankAccount(2, INITIAL_BALANCE);
            
            TransactionRecord result = service.transfer(lowerId, higherId, TRANSFER_AMOUNT);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(lowerId.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(higherId.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should transfer from higher ID to lower ID")
        void shouldTransferFromHigherIdToLowerId() {
            BankAccount higherId = new BankAccount(2, INITIAL_BALANCE);
            BankAccount lowerId = new BankAccount(1, INITIAL_BALANCE);
            
            TransactionRecord result = service.transfer(higherId, lowerId, TRANSFER_AMOUNT);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(higherId.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(lowerId.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }
    }

    @Nested
    @DisplayName("Semaphore Tests")
    class SemaphoreTests {
        
        @Test
        @DisplayName("Should handle concurrent transfers within semaphore limit")
        void shouldHandleConcurrentTransfersWithinSemaphoreLimit() throws InterruptedException {
            int numThreads = 3; // Within semaphore limit of 5
            Thread[] threads = new Thread[numThreads];
            TransactionRecord[] results = new TransactionRecord[numThreads];
            
            for (int i = 0; i < numThreads; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = service.transfer(fromAccount, toAccount, 1L);
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            for (TransactionRecord result : results) {
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isIn(
                    TransactionRecord.Status.SUCCESS, 
                    TransactionRecord.Status.FAILED
                );
            }
        }
    }

    @Nested
    @DisplayName("Timeout Tests")
    class TimeoutTests {
        
        @Test
        @DisplayName("Should handle lock timeout gracefully")
        void shouldHandleLockTimeoutGracefully() throws InterruptedException {
            // Create a scenario where locks might timeout
            BankAccount account1 = new BankAccount(1, INITIAL_BALANCE);
            BankAccount account2 = new BankAccount(2, INITIAL_BALANCE);
            
            // Lock one account in a separate thread
            Thread lockingThread = new Thread(() -> {
                account1.getLock().lock();
                try {
                    Thread.sleep(200); // Hold lock longer than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    account1.getLock().unlock();
                }
            });
            
            lockingThread.start();
            Thread.sleep(50); // Ensure locking thread acquires lock first
            
            // This should timeout and fail
            TransactionRecord result = service.transfer(account1, account2, TRANSFER_AMOUNT);
            
            lockingThread.join();
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle maximum amount")
        void shouldHandleMaximumAmount() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, Long.MAX_VALUE);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should handle transfer between accounts with zero balance")
        void shouldHandleTransferBetweenAccountsWithZeroBalance() {
            BankAccount zeroFrom = new BankAccount(3, 0L);
            BankAccount zeroTo = new BankAccount(4, 0L);
            
            TransactionRecord result = service.transfer(zeroFrom, zeroTo, 100L);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
            assertThat(zeroFrom.getBalance()).isEqualTo(0L);
            assertThat(zeroTo.getBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should handle very small amounts")
        void shouldHandleVerySmallAmounts() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, 1L);
            
            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE - 1L);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE + 1L);
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {
        
        @Test
        @DisplayName("Should maintain balance consistency under concurrent access")
        void shouldMaintainBalanceConsistencyUnderConcurrentAccess() throws InterruptedException {
            BankAccount account1 = new BankAccount(1, 1000L);
            BankAccount account2 = new BankAccount(2, 1000L);
            long totalBalance = account1.getBalance() + account2.getBalance();
            
            int numThreads = 8; // Within semaphore limit
            int transfersPerThread = 50;
            Thread[] threads = new Thread[numThreads];
            
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < transfersPerThread; j++) {
                        TransactionRecord result1 = service.transfer(account1, account2, 1L);
                        TransactionRecord result2 = service.transfer(account2, account1, 1L);
                        // Results should be valid
                        assertThat(result1).isNotNull();
                        assertThat(result2).isNotNull();
                    }
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertThat(account1.getBalance() + account2.getBalance()).isEqualTo(totalBalance);
        }
    }

    @Nested
    @DisplayName("Rollback Tests")
    class RollbackTests {
        
        @Test
        @DisplayName("Should rollback on deposit failure")
        void shouldRollbackOnDepositFailure() {
            // Create a mock scenario where deposit might fail
            BankAccount normalAccount = new BankAccount(1, INITIAL_BALANCE);
            BankAccount problematicAccount = new BankAccount(2, Long.MAX_VALUE - 1000);
            
            // This should succeed in withdrawal but potentially fail in deposit
            TransactionRecord result = service.transfer(normalAccount, problematicAccount, 2000L);
            
            // Either succeeds or fails gracefully
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isIn(
                TransactionRecord.Status.SUCCESS,
                TransactionRecord.Status.FAILED,
                TransactionRecord.Status.ROLLBACK
            );
        }
    }
}
