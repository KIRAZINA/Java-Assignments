package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TransferServiceSynchronized Tests")
class TransferServiceSynchronizedTest {

    private TransferServiceSynchronized service;
    private BankAccount fromAccount;
    private BankAccount toAccount;
    private static final long INITIAL_BALANCE = 1000L;
    private static final long TRANSFER_AMOUNT = 300L;

    @BeforeEach
    void setUp() {
        service = new TransferServiceSynchronized();
        fromAccount = new BankAccount(1, INITIAL_BALANCE);
        toAccount = new BankAccount(2, INITIAL_BALANCE);
    }

    @Nested
    @DisplayName("Transfer Tests")
    class TransferTests {
        
        @Test
        @DisplayName("Should transfer money successfully with sufficient funds")
        void shouldTransferMoneySuccessfullyWithSufficientFunds() {
            boolean result = service.transfer(fromAccount, toAccount, TRANSFER_AMOUNT);
            
            assertThat(result).isTrue();
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should fail transfer with insufficient funds")
        void shouldFailTransferWithInsufficientFunds() {
            boolean result = service.transfer(fromAccount, toAccount, INITIAL_BALANCE + 100L);
            
            assertThat(result).isFalse();
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should fail transfer to same account")
        void shouldFailTransferToSameAccount() {
            boolean result = service.transfer(fromAccount, fromAccount, TRANSFER_AMOUNT);
            
            assertThat(result).isFalse();
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should transfer exact balance")
        void shouldTransferExactBalance() {
            boolean result = service.transfer(fromAccount, toAccount, INITIAL_BALANCE);
            
            assertThat(result).isTrue();
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

        @Test
        @DisplayName("Should throw exception for both null accounts")
        void shouldThrowExceptionForBothNullAccounts() {
            assertThatThrownBy(() -> service.transfer(null, null, TRANSFER_AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts cannot be null");
        }

        @Test
        @DisplayName("Should handle zero amount transfer")
        void shouldHandleZeroAmountTransfer() {
            assertThatThrownBy(() -> service.transfer(fromAccount, toAccount, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Withdraw amount must be positive");
        }

        @Test
        @DisplayName("Should handle negative amount transfer")
        void shouldHandleNegativeAmountTransfer() {
            assertThatThrownBy(() -> service.transfer(fromAccount, toAccount, -100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Withdraw amount must be positive");
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
            
            boolean result = service.transfer(lowerId, higherId, TRANSFER_AMOUNT);
            
            assertThat(result).isTrue();
            assertThat(lowerId.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(higherId.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should transfer from higher ID to lower ID")
        void shouldTransferFromHigherIdToLowerId() {
            BankAccount higherId = new BankAccount(2, INITIAL_BALANCE);
            BankAccount lowerId = new BankAccount(1, INITIAL_BALANCE);
            
            boolean result = service.transfer(higherId, lowerId, TRANSFER_AMOUNT);
            
            assertThat(result).isTrue();
            assertThat(higherId.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(lowerId.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }

        @Test
        @DisplayName("Should handle transfers with same ID accounts")
        void shouldHandleTransfersWithSameIdAccounts() {
            BankAccount account1 = new BankAccount(1, INITIAL_BALANCE);
            BankAccount account2 = new BankAccount(1, INITIAL_BALANCE);
            
            boolean result = service.transfer(account1, account2, TRANSFER_AMOUNT);
            
            assertThat(result).isTrue();
            assertThat(account1.getBalance()).isEqualTo(INITIAL_BALANCE - TRANSFER_AMOUNT);
            assertThat(account2.getBalance()).isEqualTo(INITIAL_BALANCE + TRANSFER_AMOUNT);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle maximum amount")
        void shouldHandleMaximumAmount() {
            boolean result = service.transfer(fromAccount, toAccount, Long.MAX_VALUE);
            
            assertThat(result).isFalse(); // Should fail due to insufficient funds
            assertThat(fromAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
            assertThat(toAccount.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should handle transfer between accounts with zero balance")
        void shouldHandleTransferBetweenAccountsWithZeroBalance() {
            BankAccount zeroFrom = new BankAccount(3, 0L);
            BankAccount zeroTo = new BankAccount(4, 0L);
            
            boolean result = service.transfer(zeroFrom, zeroTo, 100L);
            
            assertThat(result).isFalse();
            assertThat(zeroFrom.getBalance()).isEqualTo(0L);
            assertThat(zeroTo.getBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should handle transfer to account with maximum balance")
        void shouldHandleTransferToAccountWithMaximumBalance() {
            BankAccount maxAccount = new BankAccount(3, Long.MAX_VALUE - 1000);
            
            boolean result = service.transfer(fromAccount, maxAccount, 500L);
            
            assertThat(result).isTrue();
            assertThat(maxAccount.getBalance()).isEqualTo(Long.MAX_VALUE - 500);
        }

        @Test
        @DisplayName("Should handle very small amounts")
        void shouldHandleVerySmallAmounts() {
            boolean result = service.transfer(fromAccount, toAccount, 1L);
            
            assertThat(result).isTrue();
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
            
            int numThreads = 10;
            int transfersPerThread = 100;
            Thread[] threads = new Thread[numThreads];
            
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < transfersPerThread; j++) {
                        service.transfer(account1, account2, 1L);
                        service.transfer(account2, account1, 1L);
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
}
