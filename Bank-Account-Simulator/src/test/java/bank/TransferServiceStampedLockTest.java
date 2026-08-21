package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TransferServiceStampedLock Tests")
class TransferServiceStampedLockTest {

    private TransferServiceStampedLock service;
    private BankAccount fromAccount;
    private BankAccount toAccount;
    private static final long INITIAL_BALANCE = 1000L;
    private static final long TRANSFER_AMOUNT = 300L;

    @BeforeEach
    void setUp() {
        service = new TransferServiceStampedLock();
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
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should fail transfer with insufficient funds")
        void shouldFailTransferWithInsufficientFunds() {
            TransactionRecord result = service.transfer(fromAccount, toAccount, INITIAL_BALANCE + 100L);

            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should fail transfer to same account")
        void shouldFailTransferToSameAccount() {
            TransactionRecord result = service.transfer(fromAccount, fromAccount, TRANSFER_AMOUNT);

            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
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

        @Test
        @DisplayName("Should throw exception for null from account")
        void shouldThrowExceptionForNullFromAccount() {
            assertThatThrownBy(() -> service.transfer(null, toAccount, TRANSFER_AMOUNT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Accounts cannot be null");
        }

        @Test
        @DisplayName("Should throw exception for null to account")
        void shouldThrowExceptionForNullToAccount() {
            assertThatThrownBy(() -> service.transfer(fromAccount, null, TRANSFER_AMOUNT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Accounts cannot be null");
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
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle maximum amount transfer")
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

        @Test
        @DisplayName("Should handle transfer to account with near-maximum balance (rollback on overflow)")
        void shouldHandleTransferToAccountWithMaximumBalance() {
            BankAccount maxAccount = new BankAccount(3, Long.MAX_VALUE - 1000);

            // This should succeed — the deposit should not overflow
            TransactionRecord result = service.transfer(fromAccount, maxAccount, 500L);

            assertThat(result.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(maxAccount.getBalance()).isEqualTo(Long.MAX_VALUE - 500);
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should maintain balance consistency under concurrent StampedLock transfers")
        void shouldMaintainBalanceConsistencyUnderConcurrentAccess() throws InterruptedException {
            int numThreads = 10;
            int transfersPerThread = 200;
            long totalExpected = fromAccount.getBalance() + toAccount.getBalance();

            Thread[] threads = new Thread[numThreads];

            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < transfersPerThread; j++) {
                        // Transfer back and forth
                        TransactionRecord r1 = service.transfer(fromAccount, toAccount, 1L);
                        TransactionRecord r2 = service.transfer(toAccount, fromAccount, 1L);
                        assertThat(r1).isNotNull();
                        assertThat(r2).isNotNull();
                    }
                });
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join(30_000);
                assertThat(t.isAlive()).isFalse();
            }

            assertThat(fromAccount.getBalance() + toAccount.getBalance()).isEqualTo(totalExpected);
        }
    }
}
