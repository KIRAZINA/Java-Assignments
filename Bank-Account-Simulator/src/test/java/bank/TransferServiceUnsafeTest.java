package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TransferServiceUnsafe Tests")
class TransferServiceUnsafeTest {

    private TransferServiceUnsafe service;
    private BankAccount fromAccount;
    private BankAccount toAccount;
    private static final long INITIAL_BALANCE = 1000L;
    private static final long TRANSFER_AMOUNT = 300L;

    @BeforeEach
    void setUp() {
        service = new TransferServiceUnsafe();
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
    @DisplayName("RandomTransfer Tests")
    class RandomTransferTests {
        
        @Test
        @DisplayName("Should perform random transfer successfully")
        void shouldPerformRandomTransferSuccessfully() {
            BankAccount[] accounts = {fromAccount, toAccount};
            long totalBefore = fromAccount.getBalance() + toAccount.getBalance();
            
            boolean result = service.randomTransfer(accounts, 500L);
            
            assertThat(result).isNotNull();
            assertThat(fromAccount.getBalance() + toAccount.getBalance()).isEqualTo(totalBefore);
        }

        @Test
        @DisplayName("Should throw exception for null accounts array")
        void shouldThrowExceptionForNullAccountsArray() {
            assertThatThrownBy(() -> service.randomTransfer(null, 500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts array cannot be null and must contain at least 2 accounts");
        }

        @Test
        @DisplayName("Should throw exception for empty accounts array")
        void shouldThrowExceptionForEmptyAccountsArray() {
            BankAccount[] emptyArray = {};
            
            assertThatThrownBy(() -> service.randomTransfer(emptyArray, 500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts array cannot be null and must contain at least 2 accounts");
        }

        @Test
        @DisplayName("Should throw exception for single account array")
        void shouldThrowExceptionForSingleAccountArray() {
            BankAccount[] singleAccount = {fromAccount};
            
            assertThatThrownBy(() -> service.randomTransfer(singleAccount, 500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts array cannot be null and must contain at least 2 accounts");
        }

        @Test
        @DisplayName("Should work with multiple accounts")
        void shouldWorkWithMultipleAccounts() {
            BankAccount account3 = new BankAccount(3, INITIAL_BALANCE);
            BankAccount[] accounts = {fromAccount, toAccount, account3};
            long totalBefore = fromAccount.getBalance() + toAccount.getBalance() + account3.getBalance();
            
            boolean result = service.randomTransfer(accounts, 500L);
            
            assertThat(result).isNotNull();
            assertThat(fromAccount.getBalance() + toAccount.getBalance() + account3.getBalance())
                .isEqualTo(totalBefore);
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
    }
}
