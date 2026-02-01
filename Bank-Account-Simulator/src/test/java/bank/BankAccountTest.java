package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BankAccount Tests")
class BankAccountTest {

    private BankAccount account;
    private static final long INITIAL_BALANCE = 1000L;
    private static final int ACCOUNT_ID = 1;

    @BeforeEach
    void setUp() {
        account = new BankAccount(ACCOUNT_ID, INITIAL_BALANCE);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should create account with correct ID and balance")
        void shouldCreateAccountWithCorrectIdAndBalance() {
            assertThat(account.getId()).isEqualTo(ACCOUNT_ID);
            assertThat(account.getBalance()).isEqualTo(INITIAL_BALANCE);
        }
    }

    @Nested
    @DisplayName("Deposit Tests")
    class DepositTests {
        
        @Test
        @DisplayName("Should deposit positive amount")
        void shouldDepositPositiveAmount() {
            long depositAmount = 500L;
            account.deposit(depositAmount);
            
            assertThat(account.getBalance()).isEqualTo(INITIAL_BALANCE + depositAmount);
        }

        @Test
        @DisplayName("Should throw exception for zero deposit")
        void shouldThrowExceptionForZeroDeposit() {
            assertThatThrownBy(() -> account.deposit(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Deposit amount must be positive");
        }

        @Test
        @DisplayName("Should throw exception for negative deposit")
        void shouldThrowExceptionForNegativeDeposit() {
            assertThatThrownBy(() -> account.deposit(-100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Deposit amount must be positive");
        }

        @Test
        @DisplayName("Should throw exception for overflow")
        void shouldThrowExceptionForOverflow() {
            BankAccount maxAccount = new BankAccount(2, Long.MAX_VALUE - 100);
            
            assertThatThrownBy(() -> maxAccount.deposit(200L))
                .isInstanceOf(ArithmeticException.class)
                .hasMessage("Balance overflow detected");
        }

        @Test
        @DisplayName("Should handle maximum safe deposit")
        void shouldHandleMaximumSafeDeposit() {
            BankAccount maxAccount = new BankAccount(2, Long.MAX_VALUE - 100);
            maxAccount.deposit(100L);
            
            assertThat(maxAccount.getBalance()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("Withdraw Tests")
    class WithdrawTests {
        
        @Test
        @DisplayName("Should withdraw amount with sufficient funds")
        void shouldWithdrawWithSufficientFunds() {
            long withdrawAmount = 300L;
            boolean result = account.withdraw(withdrawAmount);
            
            assertThat(result).isTrue();
            assertThat(account.getBalance()).isEqualTo(INITIAL_BALANCE - withdrawAmount);
        }

        @Test
        @DisplayName("Should fail withdrawal with insufficient funds")
        void shouldFailWithdrawalWithInsufficientFunds() {
            long withdrawAmount = INITIAL_BALANCE + 100L;
            boolean result = account.withdraw(withdrawAmount);
            
            assertThat(result).isFalse();
            assertThat(account.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("Should throw exception for zero withdrawal")
        void shouldThrowExceptionForZeroWithdrawal() {
            assertThatThrownBy(() -> account.withdraw(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Withdraw amount must be positive");
        }

        @Test
        @DisplayName("Should throw exception for negative withdrawal")
        void shouldThrowExceptionForNegativeWithdrawal() {
            assertThatThrownBy(() -> account.withdraw(-100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Withdraw amount must be positive");
        }

        @Test
        @DisplayName("Should withdraw exact balance")
        void shouldWithdrawExactBalance() {
            boolean result = account.withdraw(INITIAL_BALANCE);
            
            assertThat(result).isTrue();
            assertThat(account.getBalance()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Lock Tests")
    class LockTests {
        
        @Test
        @DisplayName("Should provide ReentrantLock instance")
        void shouldProvideReentrantLockInstance() {
            assertThat(account.getLock()).isNotNull();
            assertThat(account.getLock()).isInstanceOf(java.util.concurrent.locks.ReentrantLock.class);
        }
    }

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {
        
        @Test
        @DisplayName("Should return formatted string representation")
        void shouldReturnFormattedStringRepresentation() {
            String result = account.toString();
            
            assertThat(result).contains("BankAccount");
            assertThat(result).contains("id=" + ACCOUNT_ID);
            assertThat(result).contains("balance=" + INITIAL_BALANCE);
        }
    }
}
